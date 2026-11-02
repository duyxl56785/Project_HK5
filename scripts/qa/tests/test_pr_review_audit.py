import copy
import http.client
from contextlib import contextmanager
import io
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import Mock, patch
import urllib.error
import urllib.parse
import urllib.request

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import pr_review_audit as audit

REPO = "owner/repo"
HEAD = "a" * 40
BASE = "b" * 40


class TruncatedResponse:
    """Fault injected during JSON body reading, not a real credential or HTTP request."""
    def __enter__(self): return self
    def __exit__(self, *_): return False
    def read(self, *_): raise http.client.IncompleteRead(b"private-body-value", 100)


@contextmanager
def workspace_temp():
    # Keep test writes/cleanup inside this checkout, including restricted Windows executors.
    root = Path(__file__).resolve().parents[3] / ".verification" / "audit-unit-temp"
    root.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=root) as folder:
        if not Path(folder).resolve().is_relative_to(root.resolve()):
            raise AssertionError("Temporary cleanup escaped the test workspace")
        yield folder


def pr(number=1, author="duyxl56785", head_ref="feature", base_ref="main", head_repo=REPO):
    return {"number": number, "state": "open", "draft": False, "mergeable": True,
            "html_url": f"https://github.com/{REPO}/pull/{number}", "user": {"login": author},
            "head": {"sha": HEAD, "ref": head_ref, "repo": {"full_name": head_repo}},
            "base": {"sha": BASE, "ref": base_ref, "repo": {"full_name": REPO}}, "requested_reviewers": []}


def review(login=audit.TV5, state="CHANGES_REQUESTED", head=BASE):
    return {"author": {"login": login}, "state": state, "submittedAt": "2026-10-25T00:00:00Z",
            "url": "https://github.com/owner/repo/pull/1#review", "commit": {"oid": head}}


def thread(login=audit.TV5, resolved=False):
    return {"id": "thread-1", "isResolved": resolved, "isOutdated": True, "path": "file.java", "line": None,
            "comments": {"nodes": [{"author": {"login": login}, "url": "https://github.com/owner/repo/pull/1#thread"}]}}


def check(head=HEAD, conclusion="success", status="completed"):
    return {"name": "readiness", "status": status, "conclusion": conclusion, "head_sha": head,
            "html_url": "https://github.com/owner/repo/actions/runs/1"}


class FakeGitHub:
    """Deterministic API pages, including races and incomplete GraphQL responses."""
    def __init__(self, records=None):
        records = [pr()] if records is None else records
        self.records = {p["number"]: p for p in records}
        self.after = copy.deepcopy(self.records)
        self.reads = {}
        self.calls = []
        self.decision = "APPROVED"
        self.opinions = []
        self.threads = []
        self.checks = []
        self.statuses = []
        self.graph_pages = {}
        self.rest_pages = {}

    def call(self, path, payload=None):
        self.calls.append((path, payload))
        if path == "user":
            return {"login": audit.TV5}
        if path == "graphql":
            number = payload["variables"]["number"]
            field = "reviewThreads" if "reviewThreads(" in payload["query"] else "latestOpinionatedReviews"
            cursor = payload["variables"]["cursor"]
            nodes = self.threads if field == "reviewThreads" else self.opinions
            conn = self.graph_pages.get((number, field, cursor),
                {"nodes": nodes, "pageInfo": {"hasNextPage": False, "endCursor": None}})
            if "errors" in conn:
                return copy.deepcopy(conn)
            return copy.deepcopy({"data": {"repository": {"pullRequest": {
                "reviewDecision": self.decision, field: conn}}}})
        url = urllib.parse.urlsplit("https://api.github.com/" + path)
        query = urllib.parse.parse_qs(url.query)
        page = int(query.get("page", [1])[0])
        endpoint = url.path
        if (endpoint, page) in self.rest_pages:
            return copy.deepcopy(self.rest_pages[(endpoint, page)])
        if endpoint.endswith("/pulls"):
            return copy.deepcopy(list(self.records.values()) if page == 1 else [])
        if "/pulls/" in endpoint:
            number = int(endpoint.rsplit("/", 1)[1])
            self.reads[number] = self.reads.get(number, 0) + 1
            return copy.deepcopy(self.records[number] if self.reads[number] == 1 else self.after[number])
        if endpoint.endswith("/check-runs"):
            return {"check_runs": copy.deepcopy(self.checks if page == 1 else [])}
        if endpoint.endswith("/statuses"):
            return copy.deepcopy(self.statuses if page == 1 else [])
        raise AssertionError("Unexpected fake endpoint: " + path)


class CredentialsAndTransportTest(unittest.TestCase):
    def test_truncated_http_response_is_a_sanitized_audit_error(self):
        client = audit.GitHub("private-token-value", Mock(return_value=TruncatedResponse()))
        with self.assertRaises(audit.AuditError) as error:
            client.call("user")
        self.assertNotIn("private", str(error.exception))
        self.assertIn("incomplete", str(error.exception))

    def test_external_redirect_is_rejected_before_authorization_can_be_forwarded(self):
        request = urllib.request.Request("https://api.github.com/repos/owner/repo", headers={"Authorization": "Bearer private"})
        with self.assertRaises(audit.AuditError):
            audit.SameOriginRedirect().redirect_request(request, None, 302, "redirect", {}, "https://other.invalid/")

    def test_same_api_origin_redirect_remains_available_for_repository_renames(self):
        request = urllib.request.Request("https://api.github.com/repos/owner/old", headers={"Authorization": "Bearer private"})
        redirected = audit.SameOriginRedirect().redirect_request(request, None, 302, "redirect", {},
                                                                  "https://api.github.com/repos/owner/new")
        self.assertEqual("https://api.github.com/repos/owner/new", redirected.full_url)

    def test_existing_env_credential_never_starts_git_or_appears_in_output(self):
        runner = Mock(side_effect=AssertionError("must not ask Git"))
        self.assertEqual("private-value", audit.credential({"GH_TOKEN": "private-value"}, runner))
        runner.assert_not_called()

    def test_git_lookup_uses_stdin_and_does_not_put_secret_in_command_arguments(self):
        runner = Mock(return_value=subprocess.CompletedProcess([], 0, "username=viewer\npassword=private-value\n", ""))
        self.assertEqual("private-value", audit.credential({}, runner))
        self.assertEqual(["git", "credential", "fill"], runner.call_args.args[0])
        self.assertEqual("protocol=https\nhost=github.com\n\n", runner.call_args.kwargs["input"])

    def test_failed_credential_output_is_not_reflected_in_error(self):
        runner = Mock(return_value=subprocess.CompletedProcess([], 1, "password=secret\n", "secret stderr"))
        with self.assertRaises(audit.AuditError) as error:
            audit.credential({}, runner)
        self.assertNotIn("secret", str(error.exception))

    def test_rest_mutations_absolute_urls_and_graphql_mutations_are_rejected_before_network(self):
        opener = Mock()
        client = audit.GitHub("private-value", opener)
        for path, payload in [("repos/owner/repo/pulls/1/reviews", {"event": "APPROVE"}),
                              ("https://other.invalid/", None), ("../user", None),
                              ("graphql", {"query": "mutation($id:ID!){mergePullRequest}"})]:
            with self.subTest(path=path), self.assertRaises(audit.AuditError):
                client.call(path, payload)
        opener.assert_not_called()

    def test_http_error_body_and_reason_never_leak_credentials(self):
        opener = Mock(side_effect=urllib.error.HTTPError("https://api.github.com/user", 403,
                       "private-value", {}, io.BytesIO(b"private-value")))
        with self.assertRaises(audit.AuditError) as error:
            audit.GitHub("private-value", opener).call("user")
        self.assertIn("403", str(error.exception))
        self.assertNotIn("private-value", str(error.exception))


class CollectorTest(unittest.TestCase):
    def snapshot(self, api):
        return audit.Collector(api, REPO).snapshot(workers=1)

    def test_requested_tv5_and_current_changes_requested_are_independent_of_head_match(self):
        api = FakeGitHub()
        api.records[1]["requested_reviewers"] = [{"login": audit.TV5}]
        api.after = copy.deepcopy(api.records)
        api.decision = "CHANGES_REQUESTED"
        api.opinions = [review()]
        record = self.snapshot(api)["PRs"][0]
        self.assertTrue(record["TV5ChangesRequested"])
        self.assertIn("TV5_REVIEW_REQUESTED", record["observedGateSignals"])
        self.assertEqual(BASE, record["effectiveReviews"][0]["head"])
        self.assertEqual("TV2", record["workstream"])

    def test_more_than_one_graphql_page_finds_own_unresolved_thread_and_preserves_owner(self):
        api = FakeGitHub()
        api.graph_pages[(1, "reviewThreads", None)] = {
            "nodes": [thread("duyxl56785", resolved=True)],
            "pageInfo": {"hasNextPage": True, "endCursor": "next"}}
        api.graph_pages[(1, "reviewThreads", "next")] = {
            "nodes": [thread(audit.TV5)], "pageInfo": {"hasNextPage": False, "endCursor": None}}
        record = self.snapshot(api)["PRs"][0]
        self.assertEqual(1, record["TV5OpenThreads"])
        self.assertTrue(record["unresolvedThreads"][0]["outdated"])

    def test_effective_review_pagination_does_not_drop_later_changes_requested(self):
        api = FakeGitHub()
        api.graph_pages[(1, "latestOpinionatedReviews", None)] = {
            "nodes": [review("reviewer-a", "APPROVED", HEAD)],
            "pageInfo": {"hasNextPage": True, "endCursor": "more"}}
        api.graph_pages[(1, "latestOpinionatedReviews", "more")] = {
            "nodes": [review()], "pageInfo": {"hasNextPage": False, "endCursor": None}}
        self.assertTrue(self.snapshot(api)["PRs"][0]["TV5ChangesRequested"])

    def test_unknown_graphql_error_is_incomplete_not_empty_success(self):
        api = FakeGitHub()
        api.graph_pages[(1, "reviewThreads", None)] = {"errors": [{"message": "private body"}]}
        snapshot = self.snapshot(api)
        self.assertFalse(snapshot["complete"])
        self.assertEqual([], snapshot["PRs"])
        self.assertNotIn("private body", json.dumps(snapshot))

    def test_repeated_graphql_cursor_is_detected_not_looped_or_silently_truncated(self):
        api = FakeGitHub()
        page = {"nodes": [], "pageInfo": {"hasNextPage": True, "endCursor": "same"}}
        api.graph_pages[(1, "reviewThreads", None)] = page
        api.graph_pages[(1, "reviewThreads", "same")] = page
        self.assertFalse(self.snapshot(api)["complete"])

    def test_rest_pagination_reads_later_ci_page(self):
        api = FakeGitHub()
        endpoint = "/repos/owner/repo/commits/" + HEAD + "/check-runs"
        api.rest_pages[(endpoint, 1)] = {"check_runs": [check() for _ in range(100)]}
        api.rest_pages[(endpoint, 2)] = {"check_runs": [check(conclusion="failure")]}
        record = self.snapshot(api)["PRs"][0]
        self.assertEqual(101, len(record["checks"]))
        self.assertEqual("FAILED", record["ci"])

    def test_rest_page_limit_makes_snapshot_incomplete(self):
        api = FakeGitHub()
        endpoint = "/repos/owner/repo/commits/" + HEAD + "/check-runs"
        api.rest_pages[(endpoint, 1)] = {"check_runs": [check() for _ in range(100)]}
        snapshot = audit.Collector(api, REPO, max_pages=1).snapshot(workers=1)
        self.assertFalse(snapshot["complete"])
        self.assertIn("Pagination limit", snapshot["errors"][0]["error"])

    def test_head_moved_during_read_never_publishes_old_ci_as_new_head_evidence(self):
        api = FakeGitHub()
        api.checks = [check()]
        api.after[1]["head"]["sha"] = "c" * 40
        snapshot = self.snapshot(api)
        self.assertFalse(snapshot["complete"])
        self.assertEqual([], snapshot["PRs"])
        self.assertIn("changed", snapshot["errors"][0]["error"])

    def test_base_retarget_same_sha_is_also_a_source_change(self):
        api = FakeGitHub()
        api.after[1]["base"]["ref"] = "new-base"
        self.assertFalse(self.snapshot(api)["complete"])

    def test_closed_after_listing_is_not_reported_as_an_open_ready_pr(self):
        api = FakeGitHub()
        api.records[1]["state"] = "closed"
        self.assertFalse(self.snapshot(api)["complete"])

    def test_check_run_for_wrong_source_is_rejected(self):
        api = FakeGitHub()
        api.checks = [check(head=BASE)]
        self.assertFalse(self.snapshot(api)["complete"])

    def test_latest_failure_in_status_context_cannot_be_hidden_by_old_success(self):
        api = FakeGitHub()
        api.statuses = [{"context": "external", "state": "failure"},
                        {"context": "external", "state": "success"}]
        record = self.snapshot(api)["PRs"][0]
        self.assertEqual([{"context": "external", "state": "failure"}], record["statuses"])
        self.assertEqual("FAILED", record["ci"])

    def test_docs_without_checks_are_not_pass_or_automatically_ci_failed(self):
        record = self.snapshot(FakeGitHub())["PRs"][0]
        self.assertEqual("NO_CHECKS", record["ci"])
        self.assertNotIn("CI_FAILED", record["observedGateSignals"])

    def test_stacked_dependency_requires_same_repository_not_just_branch_name(self):
        api = FakeGitHub([pr(1, head_ref="parent"), pr(2, base_ref="parent"),
                          pr(3, head_ref="fork-parent", head_repo="other/fork"), pr(4, base_ref="fork-parent")])
        records = {p["number"]: p for p in self.snapshot(api)["PRs"]}
        self.assertEqual([1], records[2]["stackedOnOpenPRs"])
        self.assertEqual([], records[4]["stackedOnOpenPRs"])

    def test_readonly_collection_never_posts_rest_mutation_or_fetches_raw_review_bodies(self):
        api = FakeGitHub()
        self.snapshot(api)
        self.assertTrue(all(path == "graphql" or payload is None for path, payload in api.calls))
        self.assertFalse(any("/reviews" in path or "/comments" in path for path, _ in api.calls))


class OutputTest(unittest.TestCase):
    def test_all_state_inventory_counts_draft_subset_and_never_reads_closed_reviews(self):
        opened = pr(1); opened['title'] = 'Open source'; opened['draft'] = True
        merged = pr(2); merged.update(state='closed', merged_at='2026-10-27T00:00:00Z', title='Merged source')
        closed = pr(3); closed.update(state='closed', merged_at=None, draft=True, title='Closed draft', body='private raw body')
        api = FakeGitHub([opened, merged, closed])
        snapshot = audit.Collector(api, REPO).snapshot(workers=1, include_closed=True)
        self.assertEqual({'total': 3, 'open': 1, 'draftOpen': 1, 'merged': 1, 'closedUnmerged': 1}, snapshot['inventoryCounts'])
        self.assertEqual(['OPEN', 'MERGED', 'CLOSED'], [p['classification'] for p in snapshot['inventory']])
        self.assertEqual([1], [p['number'] for p in snapshot['PRs']])
        self.assertEqual('Open source', snapshot['PRs'][0]['title'])
        self.assertNotIn('private raw body', json.dumps(snapshot))
        self.assertTrue(all(payload['variables']['number'] == 1 for path, payload in api.calls if path == 'graphql'))

    def test_all_state_inventory_keeps_the_second_rest_page(self):
        api = FakeGitHub([pr(101)])
        closed = []
        for n in range(1, 101):
            item = pr(n); item.update(state='closed', merged_at='2026-10-26T00:00:00Z', title=f'Merged{n}')
            closed.append(item)
        api.rest_pages[('/repos/owner/repo/pulls', 1)] = closed
        api.rest_pages[('/repos/owner/repo/pulls', 2)] = [pr(101)]
        snapshot = audit.Collector(api, REPO).snapshot(workers=1, include_closed=True)
        self.assertEqual(101, snapshot['inventoryCounts']['total'])
        self.assertEqual(100, snapshot['inventoryCounts']['merged'])
        self.assertEqual(101, len(snapshot['inventory']))
        self.assertEqual([101], [p['number'] for p in snapshot['PRs']])

    def test_all_state_markdown_includes_closed_metadata_and_escapes_titles(self):
        closed = pr(2); closed.update(state='closed', merged_at=None, title='closed|title\nnext')
        snapshot = audit.Collector(FakeGitHub([pr(1), closed]), REPO).snapshot(workers=1, include_closed=True)
        rendered = audit.markdown(snapshot)
        self.assertIn('All PR inventory', rendered)
        self.assertIn('closed\\|title next', rendered)
        self.assertIn('CLOSED', rendered)
        self.assertNotIn('closed|title\nnext', rendered)

    def test_truncated_http_response_returns_exit_two_without_partial_publication(self):
        client = audit.GitHub("private-token-value", Mock(return_value=TruncatedResponse()))
        with workspace_temp() as folder:
            errors = io.StringIO()
            with patch.object(audit, "credential", return_value="private-token-value"), \
                    patch.object(audit, "GitHub", return_value=client), patch("sys.stderr", new=errors):
                self.assertEqual(2, audit.main(["--output-dir", folder]))
            self.assertIn("incomplete", errors.getvalue())
            self.assertNotIn("private", errors.getvalue())
            self.assertFalse((Path(folder) / "snapshot.json").exists())
            self.assertFalse((Path(folder) / "report.md").exists())

    def test_output_created_during_network_read_is_not_overwritten(self):
        snapshot = audit.Collector(FakeGitHub(), REPO).snapshot(workers=1)
        with workspace_temp() as folder:
            target = Path(folder) / "snapshot.json"
            def competing_writer(*_):
                target.write_text("other process snapshot", encoding="utf-8")
                return snapshot
            with patch.object(audit, "credential", return_value="private-value"), \
                    patch.object(audit.Collector, "snapshot", side_effect=competing_writer), patch("sys.stderr", new=io.StringIO()):
                self.assertEqual(2, audit.main(["--output-dir", folder]))
            self.assertEqual("other process snapshot", target.read_text(encoding="utf-8"))

    def test_ci_pending_and_neutral_are_not_success(self):
        self.assertEqual("PENDING", audit.ci_state([check(conclusion=None, status="in_progress")], []))
        self.assertEqual("NEUTRAL_OR_SKIPPED", audit.ci_state([check(conclusion="skipped")], []))
        self.assertEqual("UNKNOWN", audit.ci_state([], [{"state": "unrecognized"}]))

    def test_markdown_escapes_branch_pipe_and_newline_without_adding_rows(self):
        api = FakeGitHub([pr(base_ref="base|unsafe\ntext")])
        snapshot = audit.Collector(api, REPO).snapshot(workers=1)
        text = audit.markdown(snapshot)
        self.assertIn("base\\|unsafe text", text)
        self.assertNotIn("base|unsafe\n", text)
        self.assertIn("NO_CHECKS", text)

    def test_existing_output_is_preserved_before_credential_or_network_access(self):
        with workspace_temp() as folder:
            existing = Path(folder) / "snapshot.json"
            existing.write_text("keep", encoding="utf-8")
            with patch.object(audit, "credential") as credential, patch("sys.stderr", new=io.StringIO()):
                self.assertEqual(2, audit.main(["--output-dir", folder]))
                credential.assert_not_called()
            self.assertEqual("keep", existing.read_text(encoding="utf-8"))

    def test_partial_snapshot_is_written_with_nonzero_exit_and_no_pass_claim(self):
        api = FakeGitHub()
        api.after[1]["head"]["sha"] = "c" * 40
        snapshot = audit.Collector(api, REPO).snapshot(workers=1)
        with workspace_temp() as folder:
            with patch.object(audit, "credential", return_value="private-value"), \
                    patch.object(audit.Collector, "snapshot", return_value=snapshot), patch("sys.stdout", new=io.StringIO()):
                self.assertEqual(2, audit.main(["--output-dir", folder]))
            public = (Path(folder) / "snapshot.json").read_text(encoding="utf-8")
            self.assertFalse(json.loads(public)["complete"])
            self.assertNotIn("private-value", public)


if __name__ == "__main__":
    unittest.main()
