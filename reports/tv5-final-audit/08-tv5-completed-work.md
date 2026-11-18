# TV5 completed work — this audit session

- Re-read AGENTS.md, CONTRIBUTING.md, OWNERSHIP.md, Issues #18/#158/#160/#162 and PRs #204/#207/#209/#210.
- Verified worktree, branch, HEAD, remotes, status, diffs and fetched `origin/main=879143a...`; preserved existing untracked/staged work.
- Recomputed the 16-entry receipt manifest against committed Git blobs and working-tree bytes: 16/16 match.
- Corrected `.gitattributes` so raw payloads stay byte-stable and `SHA256SUMS.txt` remains LF text/diffable.
- Corrected receipt provenance, UAT matrix paths, official figure mapping and historical figure label.
- Parsed `test-summary.json`; inspected UTF-8 source documents without bulk conversion.
- Read exact-main hosted CI logs and TV2's 17/11 Windows rehearsal receipt; separated CI, rehearsal and final UAT.
- Audited COD/Finance source at `origin/main=879143a...`; recorded fail-closed policy and missing final settlement evidence.
- Confirmed Docker Engine 29.8.1 is available; inspected the retained pre-final stack read-only and found it incomplete (6 running / 9 exited, missing temporary secret mounts, PostgreSQL exit 255). No volumes/network were removed or modified.

Commits `bb81467`, `6ae6284`, `f74d08c` and `1a5c5ac` are pushed to `docs/tv5-prefinal-evidence-20261115`. Remote branch verified at `1a5c5ac6d682f9fba292b8e3d887014d2a59bea2`. Manifest verification is 16/16 at commit `6ae6284`; later audit commits change Markdown only. PR #204 is Ready for review with TV2 reviewer requested; merge remains pending.

## Continuation readback — 18/11/2026

- Rechecked target worktree and root worktree separately. Target HEAD is `0f3c7398c23bc07fb190b28deea4f9271e3b3180`, exactly matching the remote feature branch; root branch remains `codex/tv5-w1-acceptance-evidence` at `9c5094cc8d4164d036fd2601f60741e7ceca3562`. No tracked target-worktree modifications were present; the anomalous untracked file and root WIP were preserved.
- Live PR #204 is OPEN, Ready for review, `mergeable=true`, head `0f3c739...`, base `b2bee1d...`; compare with current main `879143a...` is 7 ahead / 28 behind. TV2 remains requested. The only submitted review is CHANGES_REQUESTED on old head `52c4036`; zero inline threads and no newer review submission.
- Live readback confirms #207/#209/#210 merged and #18/#158/#160/#162 open. TV2's 17/11 Issue #160 comment is the controlling schedule: PR #204 push by 20/11 12:00, freeze 20/11 18:00, final UAT 21/11, submission by 23/11.
- Recomputed the pre-final receipt manifest from current working-tree bytes: 16/16 MATCH. `git check-attr` confirms raw log/JSON/screenshot attributes are unset (`-text -diff`), while `SHA256SUMS.txt` is `text`, `diff`, `eol=lf`. Twenty-eight audit Markdown files decode as UTF-8; no mojibake markers were found.
- Re-read hosted Actions job logs for runs #44052552623 and #44054213307: backend Order PG16 140/140, frontend 178/178 with typecheck/build, final E2E diagnostic 24/24. The first E2E run's 23/24 failure is retained as a harness mismatch, not hidden. Run #44089780065 platform smoke job is SUCCESS.
- Docker context `desktop-linux`, Engine 29.8.1. Read-only inspection found the retained pre-final stack still incomplete: 6 running / 9 exited; PostgreSQL exit 255; eight application containers exit 127 due missing temporary secret bind-mount files. No restart, volume deletion, network deletion or data mutation was performed.
- The only report/deck sources present in `reports/submission/` remain `Bao_cao_Soopi_TV5_DRAFT_2026-11-14.docx` and `Soopi_TV5_notes_DRAFT_2026-11-14.pptx`; README explicitly says these are drafts, not the official final report. No final frozen-SHA media exists in this worktree.
