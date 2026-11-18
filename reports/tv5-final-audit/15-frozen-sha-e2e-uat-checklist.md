# Frozen SHA E2E and UAT checklist

Use only after TV2 publishes the frozen full main SHA and matching CI receipt. The 18/11 audit has not identified a frozen SHA yet.

## Before execution

- [ ] Record full 40-character main SHA, freeze timestamp, CI run URLs and result.
- [ ] Confirm the clean clone resolves to that exact SHA.
- [ ] Confirm Docker Server, Compose, ports, API health, frontend health and fake-data isolation.
- [ ] Record Compose project name, newly created volumes and test accounts without secrets.
- [ ] Confirm flags for Inventory/Finance are enabled only by approved, documented scope; record approving owner and isolated project.

## E2E and UAT evidence

- [ ] Keep raw command logs, exit codes and JUnit/XML unchanged.
- [ ] Cart two Store: record Store A/B, quantities, subtotal and HTTP/DB oracle.
- [ ] Checkout: record root Order, two SellerOrders, snapshot and same-key retry result.
- [ ] COD: record DELIVERED prerequisite, Admin actor, audit, duplicate/invalid negative result; distinguish Checkout COD selection from Admin COD collection.
- [ ] Inventory: record RESERVE, DISPATCHED/CONSUME and terminal RELEASE with before/after oracle.
- [ ] Promotion/Voucher C1: record validity, time window and customer-visible price.
- [ ] Finance: run only within approved MD04/MD12/demo scope; record fee base, HALF_UP result, immutable snapshot, Store isolation and settlement guard.
- [ ] Capture only actual UI screens, redact identifiers, then create SHA256 manifest.

## Result classification

Use PASS only for a completed case with evidence at the frozen SHA. Mark unavailable cases as FAIL, NOT_RUN, BLOCKED, or BLOCKED_DESIGN with the owner and reason. TV2's 17/11 rehearsal at `240d028...` is rehearsal evidence, not frozen-SHA acceptance. UNKNOWN and RET-05 remain outside final PASS unless their separate gates are met.
