# TV5 — toàn bộ PR, reviewers và owner handoff, 22/10/2026

Refs #5/#12/#63/#66/#67/#68. **PARTIAL / WAITING_REVIEW / WAITING_OWNER_CONFIRMATION.**
Đây là technical QA và bàn giao; không phải business approval hoặc production acceptance.

## 1. Phạm vi và tổng quan

Quét phân trang toàn bộ **51 PR** ban đầu, sau đó thêm QA PR68 do TV5 tạo: **52 PR**, **4 OPEN (1 Draft)**,
**46 MERGED**, **2 CLOSED WITHOUT MERGE (#60/#62)**. Có16Issue,15OPEN/1CLOSED; ba Issue củaTV5 #5/#12/#18 đều được đọc, không đóng hoặc mở rộng production khi thiếu gate.
Đã đọc168submittedreviews,17threads/0unresolved, reviews/comments/timeline/removal events/commits/files/checks cho mọi PR.
[Full index và từng review/request/SHA](evidence/tv5-audit-20261022/repository-reviewers.json).
Initial scan UTC 2026-10-22T05:26:28.376290+00:00; post-creation index UTC 2026-10-22T05:36:25.466419+00:00.
Tài khoản xác minh: **nguyenminhduy090-alt = TV5**, không phải duyxl56785 (TV2).

Đọc AGENTS/CONTRIBUTING/OWNERSHIP, latest QA/packet/ledger và source liên quan. Deep review source mở66/67,
latest Finance/Inventory main; không tuyên bố đã chạy mọi suite/full source audit của46 PR lịch sử.
Source của module khác giữ nguyên. Draft review chưa publish của người khác không thấy qua API.
Hai COMMENT reviews TV5 sẽ được công bố cùng report có receipt riêng; không sửa số168 của snapshot trước công bố.

## 2. PR63 và các finding

Head đã kiểm **7fd2dee57e49b2f2e14365af96ef3f2606d6cb00**, base API main03fb1b2.
TV1 [CR5580742783](https://github.com/duyxl56785/Project_HK5/pull/63#pullrequestreview-5580742783) thuộca1bce60,
vẫn hiệu lực; TV2 đã [APPROVED đúng7fd2dee](https://github.com/duyxl56785/Project_HK5/pull/63#pullrequestreview-5603157970).

| Finding | Verification / disposition | Việc còn lại |
|---|---|---|
| P2 stale current handoff U01/U02/U03/U22 | **FIXED_VERIFIED** trong7fd2dee:39/61MERGED,60/62CLOSED_UNMERGED; §12 và links QA/checklist/RTM đúng | TV1 tự cập nhật review; agent không dismiss/resolve |
| Historical provenance, initial FAIL/GREEN và owner next actions | **FIXED_VERIFIED**: inventory gốc không đổi; snapshot/timestamp riêng; không giả source60/62 vào main | Không rerun PG chỉ để đổi status; owner chọn nếu cần khôi phục nội dung60/62 |
| TV2 review tại HEAD | APPROVED docs/traceability; private raw47hashes chưa được TV2 kiểm độc lập, giới hạn được ghi rõ | Không gọi TV2 review là business policy hoặc independent raw-byte verification |
| Current source/security/Finance defect trong63 | Không có actionable defect mới được xác định; PR là docs/evidence | Bản audit này không biến proposed contracts thành approved |

TV1 đã requested từ20/10 sau push7fd2dee; **không gửi lại request trùng khi đang pending**.
Không thêm reviewer đã bị gỡ có chủ đích; mọi removal events giữ trong index. Review cũ tại PR đóng không chặn merge hiện tại.
PR63 conflict-free nhưng `mergeable_state=blocked`,0checks/0threads tại snapshot; chờ disposition của TV1.
Khi report này được push, phải kiểm head/reviews/requests/CI lại; không tự Approve/Merge.

## 3. Toàn bộ PR và reviewers

`@HEAD` là đúng SHA, `@<old>` là review commit trước. APPROVED/CR của PR đóng chỉ là lịch sử.
Cột COMMENT không thay opinionated approval/CR; chi tiết168events, request removal/actor, head/base, commits/files và refsIssue trong JSON.

| PR / state | Author | Head | Reviewers / opinionated state / requests | CI tại SHA | Decision / mergeability | Next action |
|---|---|---|---|---|---|---|
|[#6](https://github.com/duyxl56785/Project_HK5/pull/6) MERGED|duyxl56785|`09bfaf7`|—; COMMENT=—; requested=—|NO CHECKS|REVIEW_REQUIRED; N/A closed|Không request PR đóng; chỉ lịch sử/evidence.|
|[#7](https://github.com/duyxl56785/Project_HK5/pull/7) MERGED|duyxl56785|`82dea09`|lexuanbao258-collab:APPROVED@HEAD; COMMENT=duyxl56785,nguyenminhduy090-alt; requested=—|NO CHECKS|APPROVED; N/A closed|Không request PR đóng; chỉ lịch sử/evidence.|
|[#19](https://github.com/duyxl56785/Project_HK5/pull/19) MERGED|duyxl56785|`804d6f1`|nguyenminhduy090-alt:APPROVED@HEAD; lexuanbao258-collab:APPROVED@HEAD; COMMENT=duyxl56785,nguyenminhduy090-alt; requested=—|NO CHECKS|APPROVED; N/A closed|Không request PR đóng; chỉ lịch sử/evidence.|
|[#20](https://github.com/duyxl56785/Project_HK5/pull/20) MERGED|nguyenminhduy090-alt|`8d02e74`|duyxl56785:APPROVED@0222378; COMMENT=—; requested=lexuanbao258-collab,tranvanchinhbg2006-alt,duyxl56785,phamthecuong2022006-alt|NO CHECKS|APPROVED; N/A closed|Không request PR đóng; chỉ lịch sử/evidence.|
|[#21](https://github.com/duyxl56785/Project_HK5/pull/21) MERGED|nguyenminhduy090-alt|`e8efeda`|duyxl56785:APPROVED@HEAD; COMMENT=—; requested=duyxl56785|NO CHECKS|APPROVED; N/A closed|Không request PR đóng; chỉ lịch sử/evidence.|
|[#22](https://github.com/duyxl56785/Project_HK5/pull/22) MERGED|lexuanbao258-collab|`8af4cbe`|duyxl56785:APPROVED@HEAD; COMMENT=—; requested=—|NO CHECKS|APPROVED; N/A closed|Không request PR đóng; chỉ lịch sử/evidence.|
|[#23](https://github.com/duyxl56785/Project_HK5/pull/23) MERGED|lexuanbao258-collab|`bfcf5bd`|duyxl56785:APPROVED@HEAD; COMMENT=duyxl56785; requested=duyxl56785|NO CHECKS|APPROVED; N/A closed|Không request PR đóng; chỉ lịch sử/evidence.|
|[#24](https://github.com/duyxl56785/Project_HK5/pull/24) MERGED|lexuanbao258-collab|`c8a4b44`|duyxl56785:APPROVED@HEAD; COMMENT=—; requested=—|NO CHECKS|APPROVED; N/A closed|Không request PR đóng; chỉ lịch sử/evidence.|
|[#25](https://github.com/duyxl56785/Project_HK5/pull/25) MERGED|duyxl56785|`52bee93`|—; COMMENT=nguyenminhduy090-alt; requested=lexuanbao258-collab|NO CHECKS|—; N/A closed|Không request PR đóng; chỉ lịch sử/evidence.|
|[#26](https://github.com/duyxl56785/Project_HK5/pull/26) MERGED|phamthecuong2022006-alt|`c4288d5`|duyxl56785:APPROVED@HEAD; nguyenminhduy090-alt:APPROVED@HEAD; COMMENT=duyxl56785,lexuanbao258-collab,nguyenminhduy090-alt,phamthecuong2022006-alt; requested=tranvanchinhbg2006-alt|NO CHECKS|APPROVED; N/A closed|Không request PR đóng; chỉ lịch sử/evidence.|
|[#27](https://github.com/duyxl56785/Project_HK5/pull/27) MERGED|lexuanbao258-collab|`cebd1a3`|duyxl56785:APPROVED@HEAD; COMMENT=duyxl56785,nguyenminhduy090-alt; requested=—|readiness:success|APPROVED; N/A closed|Không request PR đóng; chỉ lịch sử/evidence.|
|[#28](https://github.com/duyxl56785/Project_HK5/pull/28) MERGED|tranvanchinhbg2006-alt|`382f799`|lexuanbao258-collab:APPROVED@HEAD; duyxl56785:APPROVED@bd5ff09; COMMENT=duyxl56785,lexuanbao258-collab,nguyenminhduy090-alt,phamthecuong2022006-alt,tranvanchinhbg2006-alt; requested=—|readiness:success|APPROVED; N/A closed|Không request PR đóng; chỉ lịch sử/evidence.|
|[#29](https://github.com/duyxl56785/Project_HK5/pull/29) MERGED|duyxl56785|`f8b0ff4`|nguyenminhduy090-alt:APPROVED@HEAD; COMMENT=lexuanbao258-collab,nguyenminhduy090-alt; requested=—|NO CHECKS|APPROVED; N/A closed|Không request PR đóng; chỉ lịch sử/evidence.|
|[#30](https://github.com/duyxl56785/Project_HK5/pull/30) MERGED|nguyenminhduy090-alt|`9c5094c`|lexuanbao258-collab:APPROVED@HEAD; COMMENT=duyxl56785,lexuanbao258-collab; requested=—|NO CHECKS|APPROVED; N/A closed|Không request PR đóng; chỉ lịch sử/evidence.|
|[#31](https://github.com/duyxl56785/Project_HK5/pull/31) MERGED|phamthecuong2022006-alt|`6fa018c`|duyxl56785:APPROVED@HEAD; COMMENT=duyxl56785,lexuanbao258-collab,phamthecuong2022006-alt; requested=lexuanbao258-collab,tranvanchinhbg2006-alt|readiness:success|APPROVED; N/A closed|Không request PR đóng; chỉ lịch sử/evidence.|
|[#32](https://github.com/duyxl56785/Project_HK5/pull/32) MERGED|nguyenminhduy090-alt|`0c77df6`|lexuanbao258-collab:APPROVED@HEAD; COMMENT=—; requested=—|NO CHECKS|APPROVED; N/A closed|Không request PR đóng; chỉ lịch sử/evidence.|
|[#33](https://github.com/duyxl56785/Project_HK5/pull/33) MERGED|nguyenminhduy090-alt|`11dcf39`|lexuanbao258-collab:CHANGES_REQUESTED@59dc952; COMMENT=—; requested=lexuanbao258-collab|NO CHECKS|CHANGES_REQUESTED; N/A closed|Không request PR đóng; chỉ lịch sử/evidence.|
|[#34](https://github.com/duyxl56785/Project_HK5/pull/34) MERGED|nguyenminhduy090-alt|`369b4f8`|lexuanbao258-collab:APPROVED@HEAD; COMMENT=duyxl56785; requested=duyxl56785|NO CHECKS|APPROVED; N/A closed|Không request PR đóng; chỉ lịch sử/evidence.|
|[#35](https://github.com/duyxl56785/Project_HK5/pull/35) MERGED|nguyenminhduy090-alt|`14df61b`|—; COMMENT=duyxl56785; requested=—|NO CHECKS|—; N/A closed|Không request PR đóng; chỉ lịch sử/evidence.|
|[#36](https://github.com/duyxl56785/Project_HK5/pull/36) MERGED|nguyenminhduy090-alt|`3a095dc`|—; COMMENT=duyxl56785; requested=—|NO CHECKS|—; N/A closed|Không request PR đóng; chỉ lịch sử/evidence.|
|[#37](https://github.com/duyxl56785/Project_HK5/pull/37) MERGED|duyxl56785|`ef62086`|lexuanbao258-collab:APPROVED@HEAD; COMMENT=nguyenminhduy090-alt,tranvanchinhbg2006-alt; requested=phamthecuong2022006-alt|readiness:success|APPROVED; N/A closed|Không request PR đóng; chỉ lịch sử/evidence.|
|[#38](https://github.com/duyxl56785/Project_HK5/pull/38) MERGED|phamthecuong2022006-alt|`7a8d18e`|lexuanbao258-collab:APPROVED@HEAD; COMMENT=duyxl56785,nguyenminhduy090-alt,tranvanchinhbg2006-alt; requested=—|readiness:success|APPROVED; N/A closed|Không request PR đóng; chỉ lịch sử/evidence.|
|[#39](https://github.com/duyxl56785/Project_HK5/pull/39) MERGED|phamthecuong2022006-alt|`4e4e623`|nguyenminhduy090-alt:APPROVED@HEAD; tranvanchinhbg2006-alt:APPROVED@ec2ae5a; lexuanbao258-collab:APPROVED@ec2ae5a; COMMENT=duyxl56785,nguyenminhduy090-alt; requested=—|NO CHECKS|APPROVED; N/A closed|Không request PR đóng; chỉ lịch sử/evidence.|
|[#40](https://github.com/duyxl56785/Project_HK5/pull/40) MERGED|nguyenminhduy090-alt|`e91312c`|duyxl56785:APPROVED@HEAD; COMMENT=duyxl56785; requested=—|NO CHECKS|APPROVED; N/A closed|Không request PR đóng; chỉ lịch sử/evidence.|
|[#41](https://github.com/duyxl56785/Project_HK5/pull/41) MERGED|nguyenminhduy090-alt|`aaf9d31`|—; COMMENT=—; requested=duyxl56785|NO CHECKS|—; N/A closed|Không request PR đóng; chỉ lịch sử/evidence.|
|[#42](https://github.com/duyxl56785/Project_HK5/pull/42) MERGED|nguyenminhduy090-alt|`35297ac`|—; COMMENT=—; requested=duyxl56785|NO CHECKS|—; N/A closed|Không request PR đóng; chỉ lịch sử/evidence.|
|[#43](https://github.com/duyxl56785/Project_HK5/pull/43) MERGED|nguyenminhduy090-alt|`89cd293`|—; COMMENT=tranvanchinhbg2006-alt; requested=duyxl56785,phamthecuong2022006-alt|readiness:success|—; N/A closed|Không request PR đóng; chỉ lịch sử/evidence.|
|[#44](https://github.com/duyxl56785/Project_HK5/pull/44) MERGED|nguyenminhduy090-alt|`1311f8a`|—; COMMENT=—; requested=lexuanbao258-collab,tranvanchinhbg2006-alt,duyxl56785,phamthecuong2022006-alt|readiness:success|—; N/A closed|Không request PR đóng; chỉ lịch sử/evidence.|
|[#45](https://github.com/duyxl56785/Project_HK5/pull/45) MERGED|nguyenminhduy090-alt|`9eb4ba0`|—; COMMENT=—; requested=lexuanbao258-collab,tranvanchinhbg2006-alt,duyxl56785,phamthecuong2022006-alt|NO CHECKS|—; N/A closed|Không request PR đóng; chỉ lịch sử/evidence.|
|[#46](https://github.com/duyxl56785/Project_HK5/pull/46) MERGED|nguyenminhduy090-alt|`e597daf`|—; COMMENT=—; requested=lexuanbao258-collab,tranvanchinhbg2006-alt,duyxl56785,phamthecuong2022006-alt|NO CHECKS|—; N/A closed|Không request PR đóng; chỉ lịch sử/evidence.|
|[#47](https://github.com/duyxl56785/Project_HK5/pull/47) MERGED|nguyenminhduy090-alt|`a879b08`|—; COMMENT=—; requested=duyxl56785|NO CHECKS|—; N/A closed|Không request PR đóng; chỉ lịch sử/evidence.|
|[#48](https://github.com/duyxl56785/Project_HK5/pull/48) MERGED|lexuanbao258-collab|`c0b485a`|phamthecuong2022006-alt:APPROVED@HEAD; COMMENT=duyxl56785,nguyenminhduy090-alt,tranvanchinhbg2006-alt; requested=—|NO CHECKS|APPROVED; N/A closed|Không request PR đóng; chỉ lịch sử/evidence.|
|[#49](https://github.com/duyxl56785/Project_HK5/pull/49) MERGED|phamthecuong2022006-alt|`0498d0b`|lexuanbao258-collab:APPROVED@HEAD; tranvanchinhbg2006-alt:APPROVED@HEAD; COMMENT=duyxl56785,lexuanbao258-collab,nguyenminhduy090-alt; requested=—|readiness:success|APPROVED; N/A closed|Không request PR đóng; chỉ lịch sử/evidence.|
|[#50](https://github.com/duyxl56785/Project_HK5/pull/50) MERGED|nguyenminhduy090-alt|`8084036`|—; COMMENT=—; requested=lexuanbao258-collab,tranvanchinhbg2006-alt,duyxl56785,phamthecuong2022006-alt|NO CHECKS|—; N/A closed|Không request PR đóng; chỉ lịch sử/evidence.|
|[#51](https://github.com/duyxl56785/Project_HK5/pull/51) MERGED|lexuanbao258-collab|`f98f932`|duyxl56785:APPROVED@HEAD; COMMENT=duyxl56785,nguyenminhduy090-alt; requested=—|readiness:success|APPROVED; N/A closed|Không request PR đóng; chỉ lịch sử/evidence.|
|[#52](https://github.com/duyxl56785/Project_HK5/pull/52) MERGED|duyxl56785|`a00d3db`|lexuanbao258-collab:APPROVED@HEAD; COMMENT=nguyenminhduy090-alt; requested=—|readiness:success|APPROVED; N/A closed|Không request PR đóng; chỉ lịch sử/evidence.|
|[#53](https://github.com/duyxl56785/Project_HK5/pull/53) MERGED|duyxl56785|`d08f3e5`|lexuanbao258-collab:APPROVED@HEAD; tranvanchinhbg2006-alt:APPROVED@HEAD; COMMENT=nguyenminhduy090-alt; requested=—|NO CHECKS|APPROVED; N/A closed|Không request PR đóng; chỉ lịch sử/evidence.|
|[#54](https://github.com/duyxl56785/Project_HK5/pull/54) MERGED|tranvanchinhbg2006-alt|`5955b1f`|lexuanbao258-collab:APPROVED@HEAD; COMMENT=duyxl56785,lexuanbao258-collab,phamthecuong2022006-alt; requested=nguyenminhduy090-alt|readiness:success|APPROVED; N/A closed|Không request PR đóng; chỉ lịch sử/evidence.|
|[#55](https://github.com/duyxl56785/Project_HK5/pull/55) MERGED|duyxl56785|`4e8a5ce`|lexuanbao258-collab:APPROVED@HEAD; COMMENT=—; requested=nguyenminhduy090-alt|readiness:success|APPROVED; N/A closed|Không request PR đóng; chỉ lịch sử/evidence.|
|[#56](https://github.com/duyxl56785/Project_HK5/pull/56) MERGED|phamthecuong2022006-alt|`8c88fff`|tranvanchinhbg2006-alt:APPROVED@HEAD; COMMENT=duyxl56785,lexuanbao258-collab; requested=nguyenminhduy090-alt|readiness:success|APPROVED; N/A closed|Không request PR đóng; chỉ lịch sử/evidence.|
|[#57](https://github.com/duyxl56785/Project_HK5/pull/57) MERGED|phamthecuong2022006-alt|`5532178`|lexuanbao258-collab:APPROVED@82e4cf6; COMMENT=duyxl56785; requested=lexuanbao258-collab|readiness:success|APPROVED; N/A closed|Không request PR đóng; chỉ lịch sử/evidence.|
|[#58](https://github.com/duyxl56785/Project_HK5/pull/58) MERGED|tranvanchinhbg2006-alt|`c85d74c`|duyxl56785:APPROVED@HEAD; COMMENT=duyxl56785; requested=nguyenminhduy090-alt|readiness:success|APPROVED; N/A closed|Không request PR đóng; chỉ lịch sử/evidence.|
|[#59](https://github.com/duyxl56785/Project_HK5/pull/59) MERGED|nguyenminhduy090-alt|`2a10938`|duyxl56785:APPROVED@HEAD; COMMENT=duyxl56785; requested=—|NO CHECKS|APPROVED; N/A closed|Không request PR đóng; chỉ lịch sử/evidence.|
|[#60](https://github.com/duyxl56785/Project_HK5/pull/60) CLOSED UNMERGED|tranvanchinhbg2006-alt|`e0a28de`|—; COMMENT=nguyenminhduy090-alt; requested=duyxl56785|readiness:success|REVIEW_REQUIRED; N/A closed|Không request PR đóng; chỉ lịch sử/evidence.|
|[#61](https://github.com/duyxl56785/Project_HK5/pull/61) MERGED|duyxl56785|`0af4754`|tranvanchinhbg2006-alt:APPROVED@HEAD; COMMENT=nguyenminhduy090-alt; requested=lexuanbao258-collab,phamthecuong2022006-alt|readiness:success|APPROVED; N/A closed|Không request PR đóng; chỉ lịch sử/evidence.|
|[#62](https://github.com/duyxl56785/Project_HK5/pull/62) CLOSED UNMERGED|tranvanchinhbg2006-alt|`6a79016`|—; COMMENT=nguyenminhduy090-alt; requested=duyxl56785|readiness:success|REVIEW_REQUIRED; N/A closed|Không request PR đóng; chỉ lịch sử/evidence.|
|[#63](https://github.com/duyxl56785/Project_HK5/pull/63) OPEN|nguyenminhduy090-alt|`7fd2dee`|duyxl56785:APPROVED@HEAD; lexuanbao258-collab:CHANGES_REQUESTED@a1bce60; COMMENT=duyxl56785; requested=lexuanbao258-collab|NO CHECKS|CHANGES_REQUESTED; mergeable=True,blocked|TV1 cập nhật CR; TV2 APPROVED7fd. Không re-request TV1 đang requested.|
|[#64](https://github.com/duyxl56785/Project_HK5/pull/64) MERGED|phamthecuong2022006-alt|`06ebfc4`|lexuanbao258-collab:APPROVED@HEAD; COMMENT=—; requested=—|readiness:success|APPROVED; N/A closed|Không request PR đóng; chỉ lịch sử/evidence.|
|[#65](https://github.com/duyxl56785/Project_HK5/pull/65) MERGED|phamthecuong2022006-alt|`433e446`|duyxl56785:APPROVED@HEAD; COMMENT=duyxl56785; requested=—|readiness:success|APPROVED; N/A closed|Không request PR đóng; chỉ lịch sử/evidence.|
|[#66](https://github.com/duyxl56785/Project_HK5/pull/66) OPEN DRAFT|duyxl56785|`e20263e`|—; COMMENT=—; requested=lexuanbao258-collab|readiness:success|REVIEW_REQUIRED; mergeable=True,blocked|DRAFT: TV1 review prep/runtime/wire; không production activation.|
|[#67](https://github.com/duyxl56785/Project_HK5/pull/67) OPEN|tranvanchinhbg2006-alt|`add83ec`|—; COMMENT=—; requested=nguyenminhduy090-alt,duyxl56785|readiness:success|REVIEW_REQUIRED; mergeable=True,blocked|TV5/TV2 human review storage-only; QA68 để TV3 tiếp nhận.|
|[#68](https://github.com/duyxl56785/Project_HK5/pull/68) OPEN|nguyenminhduy090-alt|`e24731f`|—; COMMENT=—; requested=tranvanchinhbg2006-alt,duyxl56785|NO CHECKS|—; mergeable=True,clean|TV3/TV2 review test supplement; base=branch67.|

## 4. Kiểm thử mới và phạm vi đã xác minh

Java21.0.7/Gradle8.10/Docker29.8.1, UTC/API1.44, disposable PostgreSQL16 Testcontainers.
Không prune/reset/xóa volume hoặc sửa dữ liệu hiện có. Logs/XML raw private; JSON là derived cases/commands/time/exit/hash.

| Exact source / invocation | Thực thi TV5 22/10 | Giới hạn / evidence |
|---|---|---|
| PR66 e20263e |236unit PASS;117integration PASS;exit0|[Cases/commands](evidence/tv5-audit-20261022/pr66.json);117=Seller26+Store65+SQL prototype26. Gồm6closed-route fixture checks, không91positive production API.|
| PR67 add83ec |11Cartunit PASS;1CartPG PASS;exit0|[Cases](evidence/tv5-audit-20261022/pr67.json); existingPG làmigration/cascade/unique, không tự chứng minh precision guard.|
| Main c2cf55a |27Inventoryunit PASS;19InventoryPG PASS;3FIN01/06/control PASS;exit0|[Current main](evidence/tv5-audit-20261022/main-current.json); có latest65delta, không dùng old5bf evidence thay433/main.|
| Main c2cf55a, rules-only invocation riêng |30Reviewunit PASS;49Financeunit PASS;exit0|[F15/F16](evidence/tv5-audit-20261022/main-rules-current.json); không DB review uniqueness/settlement/currentStorequery.|
| New Cart QA cùng testsource trên base c2cf55a |**4PASS/6assertionFAIL**,exit1|[RED](https://github.com/duyxl56785/Project_HK5/blob/e24731fa823dc50ba826b9e110a317b983d8c2e5/reports/evidence/tv5-cart-storage-20261022/red.json); tái hiện giá bị PG round/mutate trướcfix, không lỗiDocker.|
| Cùng new Cart QA trên add83ec |**10PASS/0FAIL**,exit0|[GREEN](https://github.com/duyxl56785/Project_HK5/blob/e24731fa823dc50ba826b9e110a317b983d8c2e5/reports/evidence/tv5-cart-storage-20261022/green.json); sameassertions/QAhashes, Catalog fixture +realDB, khôngliveHTTP.|

Không cộng các dòng, CI/owner/historical counts thành release total. Không hạ assertion/disable test.
Các lượt FAIL Docker trước20/21 vẫn lịch sử, không xóa hoặc gán sang lượt22/10.
FullOrder/Product/currentAuth/Gateway/positivepair/liveCOD/settlement/twoStoreE2E **NOT_RUN** trong lượt này,
trừ đúng các targeted tasks nêu trên. Fresh FIN03-case GREEN không ký M01/House/rounding.

**PR66:** đọc đủ20path, typed inputs/codec/version/response/transaction guards; không tìm thấy actionable defect mới trong prep scope.
Parser strict duplicate/unknown/wrong-kind và guarded-read mapping được test, không controller/current ADMIN/JWT production.
TV1 còn phải final own-scope wire/runtime/role-sync; không tự xác nhận thay chữ ký TV1 từ lời relay trong description.

**PR67:** guard phù hợp NUMERIC(19,2), rejects1.001/1.005/overflow trước repository, không rounding;
1.2300/zero/currentstorage max giữ value. Không dùng storagecap thay VND/business unitcap1b.
Không tìm thấy defect mới đáng yêu cầu sửa tại inspected head. Bộ10regression mới là QA child [#68](https://github.com/duyxl56785/Project_HK5/pull/68),
base `fix/tv3-cart-money-storage-exactness`, commit **e24731fa823dc50ba826b9e110a317b983d8c2e5**.
4methods/10invocations, explicit task **không tự chạy bởi readiness CI hoặc standard test**. TV3/TV2 review; TV5 không sửa productionCart.

## 5. Tất cả công việc TV5 / owner blockers

P0/P1 là ưu tiên công việc, không gán mức severity bug cho việc thiếu proposal/business signature.

| Priority | Task | Owner / ai bị chặn | Status / evidence | Next action |
|---|---|---|---|---|
|P0|PR63 disposition|TV1reviewer; TV5author chờ |FIXED_VERIFIED7fd, TV2APPROVED; activeTV1CR|TV1 đọc newdocs và cập nhật ownreview; không cần TV5 sửa lại finding cũ nếu không feedback mới|
|P0|PR67 storage QA/review input|TV5QA→TV3/TV2|DONE technical;11unit/1PG và10newPGGREEN, REDretained; PR68pushed|COMMENT evidence, humanTV5/TV2 formaldecision nếu đồng ý; TV3 tiếp nhận QA68 nếu phù hợp|
|P2|PR66 independent guarded Store/Seller QA|TV5QA→TV2/TV1|DONE scoped236/117PASS; no new defect found|COMMENT evidence; TV1security/wire review, không request TV5 self or extra duplicate|
|P2|Latest Inventory/FIN/Review rules|TV5QA→TV3/TV4|DONE exactmain27/19/3 và30/49 trong2invocations|Bàn giao per-source evidence; nooldFINrework request|
|P2|All PR/review/Issue audit+RTM/checklist|TV5→allowners|52PR/16Issue/168reviews/17threads, report/index|Commit/push63, verifyreceipt; onlygranular completedcheckboxes|
|P1|Business authority / MD01–MD10|Projectauthority/TV1coord; TV5proposer|PENDING, request6254401376 đã có|Chỉ định account/role/delegation/scope; khôngspam cùngrequest|
|P1|Productprice MD01/02/unitMD03|Authorizedhuman+TV2/TV3/TV5|Technicalcompatibility recorded; business+applicableschema/S01/security chưa đủ|Ký riêng slice1..1bVND nếu được chọn; không tự buộc chờMD04–10|
|P1|House/rate/rounding/adjustments/snapshots/cutoff|Business+TV5, TV2facts, TV3sharedOrder|MD04–10 PENDING, fee5%/House0% làrequirement chưa fullpolicy signoff|Chọn nguồnHouse, fee base/stage/version, currency/bounds, refund/revenue; noflag enable|
|P1|C06/COD/payment/refund/settlement facts|TV3+TV4, TV1auth, TV5consumer/business|Existing technical replies recorded; exact committedstate/payment/event/currentactor/atomic unique settlement thiếu|Cùngversion producer/action/schema/provenance/rollback/reconciliation; khôngsuyPAID/DELIVERED từInventory|
|P1|Auth/current Store/Product→SKU/positivepair|TV1+TV2+TV3+TV4|Owneddomain/prep/runtimeflags remainOFF; noapprovedliveproviders|TV1runtimeSHA/tests, TV2authoritativeSKU/currentStore andallowedwire; khôngmởlạibaselineS01|
|P2|Review/FinanceAPI/persistence/currentqueries/UI|TV5 vớiapprovedfacts/providers|BLOCKED; pureguards !=DBuniqueness/atomic settlement/Storequery|Triển khai chỉ khi approvedscope/dependencies, khôngđoánpolicy đểxongviệc|
|P2|Closed60/62 follow-up|TV3/TV2; TV5technicalinput đãcó|NEEDS_OWNER, không assumeintegrated hoặc lostdependency đã approved|Owner đánh giá giữtest/candidate nào; PR67/68 không thayCatalog62 hoặc journal60|
|P2|Issue18 W3 UAT/release/demo và ảnh V2|TV5QA cùngallP0owners|OPEN/BLOCKED actualReact→Gateway→DB, trustedAuth/COD/twoStore flows chưađủ|Giữ existingUAT/RTM và55pagefindinglist; khôngtạoảnh/liveE2E/seed evidencegiả hoặc tick nghiệmthuW3|
|P3|READMEsharedfacts|TV1/sharedowner, TV5handoff|Existing exactreplacement đãởreport20/10§10|Khôngtạoticket/nhắc trùng, owner update khiphùhợp|

## 6. Bàn giao để không ai chờ nhầm TV5

| Thành viên | Chờ TV5 gì | Đã giải quyết | Còn blocker / next action |
|---|---|---|---|
|TV1|QA/decision packet; PR63doccorrection|P2fixed7fd +TV2approved; currentindependentQA vàSP01–14|TV1 ownreview63/66, actual current-authority/Gateway/role-sync runtime, authoritycoordination; khôngagentapprove|
|TV2|Money compatibility và QAinput|Candidate1b scopedtechnicalinput đãcó;66fresh236/117 và67storageacceptance; no repeatbusinessvote|TV1runtime/wire; namedauthority+applicableprice signatures; review67/68 thuộcownscope|
|TV3|Cartstorage/currentFinanceQA|PR67independentchecks +supplement68RED/GREEN; FIN01/06GREENmain|Review68, humanreview67; sharedOrder/COD/schema/event/actionsource cùngTV4/TV1/business|
|TV4|Inventory/recovery/CODFinanceinput|Currentmain19providerPG+27unit; existingglobalSKU/retry/C06helperreplies recorded|Committedlifecycle/payment/reservationownership/currentactor+policy pending; noblindRELEASE/automaticrefund|
|TV5|Independent QA/test/docs delivery|Newtests/handoff ready andpushed68; auditpublication63next|HumanTV5 mayreview67afterreadingexacthead; agent cannotApprove. Businessfinalapprovalkhôngđượcủyquyền.|

TV5 không giữ một CR còn hiệu lực trên PR mở của owner khác. Requestedreview67 cần con người nếu muốn formalAPPROVED;
COMMENT technicalinput không là approval. PR63 chỉ cần chínhTV1 cập nhật CR và normalprotection; M01 pending không tựthànhgate mới cho merge riêngdocs.
Không PR mở nào được agent tuyên bố đã merge-ready vô điều kiện:63activeCR,66Draft/TV1gate,67reviewrequired,68ownertestreviewrequired.
Không còn việc code độc lập an toàn được xác định trong scope đã kiểm sau bộQA này; owner/human/runtime gates không được giải quyết bằng mock/flags.

## 7. Checklist và publication

- [x] Read complete paginated PR/reviewer/Issue inventory, preserve removal/history; no self/closed/duplicate request.
- [x] Verify oldP2 fixes at7fd, recognize currentTV2approval; no repeated human39action.
- [x] Execute current66/67/main scopes and newCartRED/GREEN; save raw originals/hash and derived summaries.
- [x] Commit/push QA e24731f and create supplemental PR68, requestTV3/TV2 once.
- [ ] Audit/docs63 commit/push, COMMENT66/67, Issue5/12updated and reviewer verification: tick onGitHub only after actualreceipt.
- [ ] Humanreview63/66/67/68 and applicablebusiness/runtime gates; Issue5/12remainOPEN/PARTIAL.

No Approve/Merge/Close, noFinance/pairflag, no sharedproduction/schema/migration edits. InitialtestsdiagnosticRED kept.
Publication receipts for the later audit commit/reviews/Issueedits belong onGitHub and private `.verification` after verification;
this file does not predict their success. A next push can require TV2 re-review if material; TV1 already requested stays unchanged.
