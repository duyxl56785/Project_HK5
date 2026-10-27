#!/usr/bin/env python3
"""Read-only GitHub PR/reviewer snapshot. No review, comment, merge or request mutations.

Promotes the pagination/auth/source-attribution approach used by TV5's private QA
scripts into a dependency-free, testable tool. Never publish API bodies or tokens.
"""
import argparse
import concurrent.futures
import datetime as dt
import http.client
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request

API = "https://api.github.com/"
TV5 = "nguyenminhduy090-alt"
OWNERS = {"duyxl56785": "TV2", "tranvanchinhbg2006-alt": "TV3"}


class AuditError(Exception):
    """A sanitized incomplete-audit error, without HTTP bodies/headers/credentials."""


class SameOriginRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, response, code, message, headers, new_url):
        target = urllib.parse.urlsplit(new_url)
        if (target.scheme, target.netloc) != ("https", "api.github.com"):
            raise AuditError("Refusing a redirect outside GitHub API; credentials were not forwarded")
        return super().redirect_request(request, response, code, message, headers, new_url)


def credential(env=None, runner=None):
    env = os.environ if env is None else env
    runner = subprocess.run if runner is None else runner
    for name in ("GH_TOKEN", "GITHUB_TOKEN"):
        if env.get(name):
            return env[name]
    try:
        result = runner(["git", "credential", "fill"], input="protocol=https\nhost=github.com\n\n",
                        text=True, capture_output=True, timeout=30, check=False)
    except (OSError, subprocess.TimeoutExpired):
        raise AuditError("Git credential lookup failed; authenticate GitHub first") from None
    values = dict(line.split("=", 1) for line in result.stdout.splitlines() if "=" in line)
    if result.returncode or not values.get("password"):
        raise AuditError("No GitHub credential available; use the existing Git login or GH_TOKEN")
    return values["password"]


class GitHub:
    def __init__(self, token, opener=None, timeout=30):
        self.token = token
        self.opener = urllib.request.build_opener(SameOriginRedirect()).open if opener is None else opener
        self.timeout = timeout

    def call(self, path, payload=None):
        # Only our fixed GraphQL query transport may POST. No arbitrary URLs or REST mutations.
        if path.startswith(("http:", "https:", "/")) or ".." in path.split("/"):
            raise AuditError("Only relative GitHub API paths are allowed")
        if payload is not None and (path != "graphql" or not payload.get("query", "").lstrip().startswith("query(")):
            raise AuditError("This tool permits GET and GraphQL queries only")
        request = urllib.request.Request(API + path,
            data=None if payload is None else json.dumps(payload).encode("utf-8"),
            headers={"Authorization": "Bearer " + self.token, "Accept": "application/vnd.github+json",
                     "X-GitHub-Api-Version": "2022-11-28"})
        try:
            with self.opener(request, timeout=self.timeout) as response:
                return json.load(response)
        except urllib.error.HTTPError as failure:
            raise AuditError(f"GitHub API HTTP {failure.code}; check access/rate limits") from None
        except (urllib.error.URLError, http.client.HTTPException, TimeoutError, OSError, ValueError):
            raise AuditError("GitHub request failed or returned invalid JSON; audit is incomplete") from None


class Collector:
    def __init__(self, client, repo, tv5=TV5, max_pages=100):
        if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_.-]*/[A-Za-z0-9][A-Za-z0-9_.-]*", repo):
            raise AuditError("Repository must have the form owner/name")
        if max_pages < 1:
            raise AuditError("max_pages must be positive")
        self.client, self.repo, self.tv5, self.max_pages = client, repo, tv5, max_pages
        self.prefix = "repos/" + repo + "/"

    def pages(self, path, key=None):
        items = []
        for page in range(1, self.max_pages + 1):
            data = self.client.call(path + ("&" if "?" in path else "?") + f"per_page=100&page={page}")
            values = data.get(key) if key and isinstance(data, dict) else data
            if not isinstance(values, list):
                raise AuditError("Unexpected paginated response; refusing a truncated snapshot")
            items.extend(values)
            if len(values) < 100:
                return items
        raise AuditError("Pagination limit reached; snapshot is incomplete")

    def connection(self, number, field, selection):
        owner, name = self.repo.split("/")
        query = """query($owner:String!,$name:String!,$number:Int!,$cursor:String){
          repository(owner:$owner,name:$name){pullRequest(number:$number){reviewDecision
            %s(first:100,after:$cursor){pageInfo{hasNextPage endCursor}nodes{%s}}
          }}}""" % (field, selection)
        values, cursor, seen = [], None, set()
        for _ in range(self.max_pages):
            data = self.client.call("graphql", {"query": query,
                "variables": {"owner": owner, "name": name, "number": number, "cursor": cursor}})
            if data.get("errors"):
                raise AuditError("GraphQL reported errors; audit is incomplete")
            try:
                pr = data["data"]["repository"]["pullRequest"]
                connection = pr[field]
                values.extend(connection["nodes"])
                info = connection["pageInfo"]
            except (KeyError, TypeError):
                raise AuditError("Missing GraphQL PR/connection; audit is incomplete") from None
            if not info["hasNextPage"]:
                return pr["reviewDecision"], values
            cursor = info["endCursor"]
            if not cursor or cursor in seen:
                raise AuditError("GraphQL cursor did not advance; audit is incomplete")
            seen.add(cursor)
        raise AuditError("GraphQL pagination limit reached; audit is incomplete")

    def pr(self, number):
        path = self.prefix + f"pulls/{number}"
        before = self.client.call(path)
        if before["state"] != "open":
            raise AuditError("PR closed after the open listing; rerun snapshot")
        head, base = before["head"]["sha"], before["base"]["sha"]
        decision, reviews = self.connection(number, "latestOpinionatedReviews",
                "author{login} state submittedAt url commit{oid}")
        thread_decision, threads = self.connection(number, "reviewThreads",
                "id isResolved isOutdated path line comments(first:1){nodes{author{login} url}}")
        checks = self.pages(self.prefix + f"commits/{head}/check-runs", "check_runs")
        statuses = self.pages(self.prefix + f"commits/{head}/statuses")
        after = self.client.call(path)
        if (head, base, before["base"]["ref"], before["state"]) != \
                (after["head"]["sha"], after["base"]["sha"], after["base"]["ref"], after["state"]):
            raise AuditError("PR source/state changed during audit; rerun instead of mixing evidence")
        if decision != thread_decision:
            raise AuditError("Review decision changed during audit; rerun snapshot")
        if any(check.get("head_sha") != head for check in checks):
            raise AuditError("Check-run head mismatch; refusing mixed-source CI evidence")
        effective = [{"author": (review.get("author") or {}).get("login", "[deleted]"),
                      "state": review["state"], "head": (review.get("commit") or {}).get("oid"),
                      "url": review["url"], "submittedAt": review["submittedAt"]} for review in reviews]
        unresolved = []
        for thread in threads:
            if thread["isResolved"]:
                continue
            comments = thread["comments"]["nodes"]
            first = comments[0] if comments else {}
            unresolved.append({"id": thread["id"], "path": thread["path"], "line": thread["line"],
                "outdated": thread["isOutdated"], "author": (first.get("author") or {}).get("login", "[unknown]"),
                "url": first.get("url")})
        # /statuses is newest first. Old success must not hide the latest failure in the same context.
        latest = {}
        for status in statuses:
            latest.setdefault(status["context"], {"context": status["context"], "state": status["state"]})
        ci_checks = [{"name": c["name"], "status": c["status"], "conclusion": c["conclusion"],
                      "url": c.get("html_url")} for c in checks]
        requested = [reviewer["login"] for reviewer in after["requested_reviewers"]]
        signals = []
        if after["draft"]:
            signals.append("DRAFT")
        if decision in ("CHANGES_REQUESTED", "REVIEW_REQUIRED"):
            signals.append(decision)
        if unresolved:
            signals.append("UNRESOLVED_THREADS")
        if after.get("mergeable") is False:
            signals.append("MERGE_CONFLICT")
        ci = ci_state(ci_checks, list(latest.values()))
        if ci in ("FAILED", "PENDING"):
            signals.append("CI_" + ci)
        if self.tv5 in requested:
            signals.append("TV5_REVIEW_REQUESTED")
        author = after["user"]["login"]
        return {"number": number, "url": after["html_url"], "author": author,
            "workstream": OWNERS.get(author, "OTHER"), "state": after["state"], "draft": after["draft"],
            "head": head, "headRef": after["head"]["ref"], "headRepo": (after["head"].get("repo") or {}).get("full_name"),
            "base": base, "baseRef": after["base"]["ref"], "baseRepo": (after["base"].get("repo") or {}).get("full_name"),
            "mergeable": after.get("mergeable"), "reviewDecision": decision, "requestedReviewers": requested,
            "effectiveReviews": effective, "unresolvedThreads": unresolved, "checks": ci_checks,
            "statuses": list(latest.values()), "ci": ci, "observedGateSignals": signals,
            "TV5ChangesRequested": any(r["author"] == self.tv5 and r["state"] == "CHANGES_REQUESTED" for r in effective),
            "TV5OpenThreads": sum(t["author"] == self.tv5 for t in unresolved),
            "approvalAttribution": "Review metadata only: read review body/diff to verify its scope; no auto-approval/readiness inference"}

    def snapshot(self, workers=4):
        if workers not in range(1, 5):
            raise AuditError("workers must be between 1 and 4")
        viewer = self.client.call("user")["login"]
        opened = self.pages(self.prefix + "pulls?state=open&sort=created&direction=asc")
        numbers = [pr["number"] for pr in opened]
        if len(numbers) != len(set(numbers)):
            raise AuditError("Open-PR pagination changed; rerun instead of hiding duplicate observations")
        records, errors = [], []
        def collect(number):
            try:
                return self.pr(number), None
            except AuditError as failure:
                return None, {"number": number, "error": str(failure)}
        with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as pool:
            for record, error in pool.map(collect, numbers):
                (errors if error else records).append(error if error else record)
        records.sort(key=lambda pr: pr["number"])
        for pr in records:
            pr["stackedOnOpenPRs"] = [parent["number"] for parent in records if parent["number"] != pr["number"]
                and parent["headRef"] == pr["baseRef"] and parent["headRepo"] is not None
                and parent["headRepo"] == pr["baseRepo"]]
        return {"schemaVersion": 1, "generatedAtUtc": dt.datetime.now(dt.timezone.utc).isoformat(),
            "repository": self.repo, "viewer": viewer, "TV5": self.tv5, "complete": not errors,
            "listedOpenPRs": len(numbers), "PRs": records, "errors": errors,
            "limits": ["Read-only metadata observations, not an atomic GitHub transaction or source/runtime review",
                "NO_CHECKS/UNKNOWN are not PASS or proof of failure; consult applicable repository rules",
                "Approval metadata/head can outlive or be rewritten after source changes: read actual review body",
                "Stacked branch relations are not a complete contract/deployment dependency graph",
                "No comments, review requests, Approve, merge, credentials or raw CI output are published"]}


def ci_state(checks, statuses):
    if not checks and not statuses:
        return "NO_CHECKS"
    if any(c["conclusion"] in ("failure", "cancelled", "timed_out", "action_required", "startup_failure", "stale") for c in checks) \
            or any(s["state"] in ("failure", "error") for s in statuses):
        return "FAILED"
    if any(c["status"] != "completed" or c["conclusion"] is None for c in checks) \
            or any(s["state"] == "pending" for s in statuses):
        return "PENDING"
    if all(c["conclusion"] in ("success", "neutral", "skipped") for c in checks) \
            and all(s["state"] == "success" for s in statuses):
        return "SUCCESS" if any(c["conclusion"] == "success" for c in checks) or statuses else "NEUTRAL_OR_SKIPPED"
    return "UNKNOWN"


def cell(value):
    return str(value).replace("|", "\\|").replace("\r", " ").replace("\n", " ")


def markdown(snapshot):
    lines = ["# Read-only PR review audit", "", f"UTC: {snapshot['generatedAtUtc']}",
        f"Repository: {snapshot['repository']} · viewer: {snapshot['viewer']} · complete: {snapshot['complete']}", "",
        "| PR | HEAD / base | Author | Requested | Effective reviews | Open threads | CI | Observed gates / stacked on |",
        "|---|---|---|---|---|---|---|---|"]
    for pr in snapshot["PRs"]:
        reviews = ", ".join(r["author"] + ":" + r["state"] + "@" + (r["head"] or "?")[:7] for r in pr["effectiveReviews"])
        gates = ", ".join(pr["observedGateSignals"]) or "No metadata gate observed; manual scope/rules review required"
        deps = ", ".join("#" + str(n) for n in pr["stackedOnOpenPRs"])
        values = [f"[#{pr['number']}]({pr['url']})", pr["head"] + " / " + pr["baseRef"] + "@" + pr["base"][:7],
                  pr["author"], ", ".join(pr["requestedReviewers"]) or "—", reviews or "—",
                  len(pr["unresolvedThreads"]), pr["ci"], gates + ("; base→" + deps if deps else "")]
        lines.append("| " + " | ".join(cell(v) for v in values) + " |")
    lines += ["", "## Incomplete records", ""]
    lines += [f"- #{error['number']}: {cell(error['error'])}" for error in snapshot["errors"]] or ["None."]
    lines += ["", "## Limits", ""] + ["- " + limit for limit in snapshot["limits"]]
    return "\n".join(lines) + "\n"


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", default="duyxl56785/Project_HK5")
    parser.add_argument("--tv5", default=TV5)
    parser.add_argument("--workers", type=int, default=4)
    parser.add_argument("--output-dir", type=Path, default=Path(".verification/pr-review-audit") /
                        dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%S%fZ"))
    args = parser.parse_args(argv)
    try:
        # Never overwrite a user's earlier snapshot, even before doing network work.
        if any((args.output_dir / name).exists() for name in ("snapshot.json", "report.md")):
            raise AuditError("Output files already exist; choose a new output directory")
        snapshot = Collector(GitHub(credential()), args.repo, args.tv5).snapshot(args.workers)
        args.output_dir.mkdir(parents=True, exist_ok=True)
        with (args.output_dir / "snapshot.json").open("x", encoding="utf-8") as stream:
            stream.write(json.dumps(snapshot, ensure_ascii=False, indent=2) + "\n")
        with (args.output_dir / "report.md").open("x", encoding="utf-8") as stream:
            stream.write(markdown(snapshot))
        print(f"Read-only snapshot: {len(snapshot['PRs'])}/{snapshot['listedOpenPRs']} PRs; complete={snapshot['complete']}; {args.output_dir}")
        return 0 if snapshot["complete"] else 2
    except AuditError as failure:
        print("Audit incomplete: " + str(failure), file=sys.stderr)
        return 2
    except (OSError, ValueError, KeyError, TypeError):
        print("Audit incomplete: invalid input/response or output unavailable; no approval inferred", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
