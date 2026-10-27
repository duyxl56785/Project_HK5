# Read-only PR/reviewer audit

Refs #5/#12. Python 3.10+, standard library only. This promotes the collector/auth/pagination approach
from TV5's private `.verification` scripts into a shareable CLI with deterministic tests, rather than
adding another notification bot. There was no tracked PR-audit CLI in the inspected main.

```powershell
python scripts/qa/pr_review_audit.py --repo duyxl56785/Project_HK5
python -m unittest discover -s scripts/qa/tests -v
```

The CLI uses `GH_TOKEN`/`GITHUB_TOKEN` when already supplied, otherwise the existing
`git credential fill` login for github.com. It never prompts for a token in a command argument,
prints credentials, or stores them in the snapshot. It refuses redirects outside the GitHub API.
Missing authentication or incomplete reads return exit2, not a fabricated empty/green result.

Default outputs are timestamped `.verification/pr-review-audit/<UTC>/snapshot.json` and `report.md`.
Use `--output-dir <new-directory>` to choose another location. Files are created exclusively and are
never overwritten, including a concurrent writer race. Existing earlier evidence is preserved.

## What it reads

- Paginated OPEN PRs, actual head/base/author/requested reviewers, GitHub review decision and effective
  opinionated reviews, including an older still-effective CHANGES_REQUESTED.
- All pages of review threads and their original author; an outdated unresolved thread remains open.
- All check-run/status pages at the recorded head. Old success cannot hide a newer status failure in
  the same context. Empty checks mean `NO_CHECKS`, never PASS or an automatically required CI failure.
- TV5 requests, effective TV5 changes requested and own open-thread counts; TV2/TV3 author workstreams.
- Stacked open-branch dependencies only when the base and parent head belong to the same repository.

Each PR's head/base/state is re-read after its evidence. A changed source, missing GraphQL data,
nonadvancing cursor, wrong CI head or page-limit overflow produces an incomplete record/exit2.
Other successfully observed PRs are retained and the Markdown report identifies incomplete records.
`--workers` accepts1–4; it does not spawn coding agents or run tests of the PRs.

## Limits

The report is metadata QA, **not** source review, runtime PASS, a full deployment dependency graph or
an atomic GitHub transaction. Read actual review bodies and repository rules before interpreting an
approval: GitHub can retain/rewrite review head metadata after a push. No automatic READY/approval
conclusion is made. A no-check docs PR is not assumed broken, and CI SUCCESS is not live E2E.

The tool sends only REST GETs and its fixed GraphQL queries. It cannot post comments/reviews, request
reviewers, resolve threads, Approve, merge, close, push or enable Finance/Inventory. It deliberately
omits PR bodies/diffs/comment contents/raw CI logs from published outputs. Check publication policy
before committing a snapshot; credentials and private raw runtime logs never belong in evidence.

## Regression coverage

27 offline tests exercise pagination, source changes, closed/retargeted PRs, incomplete GraphQL,
old effective review attribution, wrong-head CI, latest external status, no-check/pending/skipped
classification, same-name fork dependency, GET/query-only transport, credential/error/redirect
safety, Markdown escaping, existing evidence and concurrent output protection. These are fake API
fixtures and temporary local files, **not GitHub or application integration tests**.

The first development run had22PASS/2file-I/O errors because the managed Windows default temporary
directory was unwritable. The test harness now uses a verified directory inside this checkout;
assertions were retained. The later expanded27-case run passes. This environment failure is distinct
from a PR business defect and from live network verification, whose receipt is recorded separately.
