# Final UAT run `tv2-uat-eaf3bf2-20261121`

- **Frozen SHA:** `eaf3bf270255b80db4525f836bfae7e495dc8651`
- **Run time:** 21/11/2026 to 22/11/2026 (+07:00)
- **Operator:** TV2 (`duyxl56785`)
- **Result:** 34 PASS, 1 PARTIAL (PROMO-02), 2 BLOCKED (UAT-16, UAT-17), 0 FAIL. No P0 or P1 found.
- **Announcement:** [Issue #158, result comment](https://github.com/duyxl56785/Project_HK5/issues/158#issuecomment-7075353935)

## Contents

- `run-manifest.json`: SHA, environment, image digests, schema versions, flags, CI links and the status of every case.
- `<test-id>/manifest.json`: one per case (37 cases), in the layout of `evidence-manifest.template.json`.
- `<test-id>/raw/`: the receipts written by the runner scripts during the run, copied byte for byte.
  - Each JSON receipt holds the HTTP status and the read-only DB results the script compared.
  - C2-09 also has its JUnit XML.
- `checksums.sha256`: SHA-256 of every file in this folder except itself. Check with `sha256sum -c checksums.sha256` from this folder.

## Reading the receipts

- **Superseded receipts.** A file named `*.attempt1-*.json` or `*.premature-*.json` is an earlier receipt, kept unchanged and not counted.
  - `attempt1` files failed because of a script error: wrong column, wrong Admin status filter, or amount parsing.
  - `premature` files were run before the clearance campaign started.
  - The case `manifest.json` names them.
- **PROMO-02.** The Super Sale was checked inside its window. The Flash Sale was checked only before and after its window (Saturday 22:00–24:00). The in-window check was not run, because Docker on the UAT machine had stopped.
- **UAT-16, UAT-17.** UNKNOWN claim conflict and lost response were not run. They need a reviewed runner, a provider receipt and a separate approval. These are known limitations.
- **UAT-12.** Return without restock passed. RET-05 (exactly-once restock) stays BLOCKED.

## Not included

- No screenshots, logs, JWTs or traces are included.
- The report figures (Hình 6.1–6.8) were captured on the same stack and SHA. They are in the official DOCX, which is kept outside the repository.
- All accounts and data are fake (`@hk5.local`). The cash amounts are recorded reconciliation figures, not real money transfers.
