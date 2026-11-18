# Reviewer message drafts

## PR #204 after push

The three review items have been addressed in the new head: the 16 raw evidence files are byte-stable and the checksum manifest remains readable/diffable; the UAT matrix references existing log paths; and source provenance distinguishes tested `main@552e8eb` (merge commit #202) from PR #194 head `42a09be`. The official mapping is corrected: Figure 6.4 Admin Seller approval, 6.5 two-Store Cart, 6.6 root Order plus two SellerOrders. Working-tree SHA verification is 16/16; final committed-blob verification is recorded in the manifest commit. This is documentation/evidence-only, so runtime suites were not rerun. Please re-review the new head.

## TV2 environment / freeze decision

Please confirm the controlling schedule: the task brief states freeze 19/11 and final UAT 20/11, while the latest Issue #160 update states freeze 20/11 18:00 and final UAT 21/11. Please publish the frozen full SHA, matching CI run, approved isolated runner and clean-clone receipt before final acceptance.
