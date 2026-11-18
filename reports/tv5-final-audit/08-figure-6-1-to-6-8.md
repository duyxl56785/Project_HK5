# Figure 6.1–6.8 audit — source report and official crosswalk

Sources inspected: `reports/submission/Bao_cao_Soopi_TV5_DRAFT_2026-11-14.docx`, `docs/project-report/tv3/CHECKOUT.md`, `docs/project-report/TV5_APPENDIX_B_CAPTURE_SHEET.md`, Issue #162 and merged PR #210.

Important distinction: Appendix B.1–B.5 are **not** Figures 6.1–6.8. The draft Word file contains a caption for Figure 6.1 (system component architecture). The Issue #162 / PR #210 owner-approved crosswalk assigns Figures 6.4–6.6 as below. The older TV3 source that labels Checkout as Figure 6.4 is historical and superseded for the official report mapping.

| Figure | Caption / mapping supported by source | Evidence in current draft | Status / next action |
|---|---|---|---|
| 6.1 | Kiến trúc thành phần hệ thống | Caption present in DOCX; diagram is report content, not B.1 Catalog screenshot | PRESENT_IN_DRAFT; TV2 verify rendered image/layout |
| 6.2 | No Figure 6.2 caption found; `6.2 Phân chia trách nhiệm` is a section heading | No figure caption/evidence identified | NOT_DEFINED_IN_CURRENT_DRAFT; TV2 confirm whether figure is required |
| 6.3 | No Figure 6.3 caption found; `6.3 Quyết định kiến trúc` is a section heading | No figure caption/evidence identified | NOT_DEFINED_IN_CURRENT_DRAFT; TV2 confirm whether figure is required |
| 6.4 | Admin Seller approval/management (official Issue #162 / merged PR #210 mapping) | No final image located | NOT_CAPTURED; TV2 capture on frozen SHA |
| 6.5 | Cart with two Stores (official Issue #162 / merged PR #210 mapping) | TV2 privately inspected `Hinh_6_5_Cart_2_Store_REHEARSAL.png` from 17/11 rehearsal ZIP, app SHA `240d028...` | REHEARSAL_ONLY; NOT_SUBMISSION_MEDIA; recapture on frozen SHA |
| 6.6 | One root Order with two SellerOrders (official Issue #162 / merged PR #210 mapping) | TV2 privately inspected `Hinh_6_6_1_Root_2_SellerOrders_REHEARSAL.png`; read-only receipt showed root 1 / child 2 | REHEARSAL_ONLY; NOT_SUBMISSION_MEDIA; recapture on frozen SHA |
| 6.7 | No Figure 6.7 caption found in current draft or inspected crosswalk sources | No final image located | NOT_DEFINED_IN_CURRENT_DRAFT; TV2 confirm if required |
| 6.8 | No Figure 6.8 caption found in current draft or inspected crosswalk sources | No final image located | NOT_DEFINED_IN_CURRENT_DRAFT; TV2 confirm if required |

TV2 rehearsal also verified Seller own-order 200 / cross-Store 404, but no Figure 6.7 caption or approved submission image was found. Finance remains governed by B.4/B.5 and the separate business approval gate; do not invent a Figure 6.8 Finance caption.

Appendix evidence remains tracked separately in `TV5_APPENDIX_B_CAPTURE_SHEET.md`: B.1 Catalog, B.2 two-Store Cart, B.3 root Order/child orders, B.4 Seller/Finance, B.5 Admin moderation/COD. None of B.1–B.5 is final UAT media yet. Each final capture needs full frozen SHA, run ID, timestamp, image SHA256 and matching API/DB oracle.
