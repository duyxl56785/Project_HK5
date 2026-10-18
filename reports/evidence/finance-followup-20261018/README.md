# Finance persistence follow-up evidence

[Report](../../TV5_FINANCE_FOLLOWUP_2026-10-18.md), Refs #5/#12/#28/#41.

- parent-head: fresh execution at merged parent55bf5ff,72unit+13PG PASS.
- supplementary: fresh execution with2 changed Finance tests, committed unchanged ascd2c0be,
  73unit+17PG PASS. source-binding.json explains HEAD-at-launch vs tested working-tree delta.
- Original build.log/JUnit XML/run.json/exit/Docker metadata unchanged; no raw record patched after run.
- run.json records precise UTC start/finish, executor, commands, environment and per-task results.
- Both runs0failures/errors/skips,0missing old containers/volumes. Expected DB22003 is asserted rejection.
- manifest.sha256 hashes43 files, excluding README/attributes/manifest. Check committed blob bytes too.
- Code/docs and raw generated evidence whitespace checked separately; never normalize raw CRLF.
- No live E2E, settlement ledger or Store authorization proof. Do not sum overlapping runs/history/TV2/V2.
