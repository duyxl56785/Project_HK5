# Evidence manifest audit — committed Git blobs

Receipt: `reports/minh-chung-kiem-thu/tv5-prefinal-552e8eb-20261115/`. The manifest lists 16 unique payload files. Every listed checksum was generated from the committed payload blob at `bb81467bd230cc21e0365f6938fb036d70caa66` and independently verified against both the final Git blob and working-tree bytes at `6ae62847d7d2f91db34d6557f6f697f46d49d78e`.

| File | SHA-256 in manifest | Actual SHA-256 at final HEAD | Status | Note |
|---|---|---|---|---|
| `logs/backend/auth-persistence-isolated-pass.log` | `f062a4664e69946ecb2e4d8cd77a8777d8ff825099ee14a964896b4c66427581` | Same | MATCH | Raw backend log |
| `logs/backend/forced-serial-build-pass.log` | `ecb02a08589af200e37aeeb043f4798139f94b2fdcec5ca4a614b79bb1161f7d` | Same | MATCH | Raw backend build log |
| `logs/backend/initial-parallel-auth-timeout.log` | `5b427c0a755930cebabbcf956f383d6f58e9182597bd282449d9090ec2a90ad1` | Same | MATCH | Original timeout/failure log retained |
| `logs/backend/isolated-smoke-pass.log` | `b59a5f5e72d3c311b81dc25eb903e991996d3769dddb523e976938513c81d6bf` | Same | MATCH | Isolated smoke log |
| `logs/backend/serial-build-cached-pass.log` | `b2f965b134f531bff519b79d075cdc8022dd3e65c5634741b06016f3f4d522c9` | Same | MATCH | Cached serial build log |
| `logs/backend/wrapper-class-not-found-accented-path.log` | `f608ad5438238e34cabb7030cd68b104989ecba57fcc0628991169642454c9e5` | Same | MATCH | Original path failure retained |
| `logs/frontend/frontend-build.log` | `65d61e80b902bf268d69dd9513ca2c4bcb1fb704ca64109ea74882bf034f1a2d` | Same | MATCH | Frontend build log |
| `logs/frontend/frontend-typecheck.log` | `7fed08d7b81cd0d0d3f4c29060ebdfbf5c5e4c8c4e3b6f0cbb1987846618e19a` | Same | MATCH | Frontend typecheck log |
| `logs/frontend/frontend-unit-tests.log` | `eaf12005014206abe307f484acba56858364944c833a81e7427aeea31cec3ff0` | Same | MATCH | 178 frontend unit tests log |
| `logs/frontend/npm-ci.log` | `8a31d1d051eafac9c573c5e360ee5da903b4a715af4ead11bc30a833c2dd2b30` | Same | MATCH | Dependency install log |
| `products-db-readonly.txt` | `ce3925f885882a21267d257983f0da53f7571abfaad3e9e1ce49b10908c6f3b0` | Same | MATCH | Read-only product snapshot |
| `rehearsal-before-uat.json` | `4b02eec8776a504ea8e29206270d923efa8df4a2f9a766fced1f96f4d9634fc2` | Same | MATCH | Pre-UAT environment snapshot |
| `screenshots/B1-catalog-pre-final.png` | `66510614854578543463737fbb653e630396208b872728041693c85269c03811` | Same | MATCH | Real but partial pre-final Catalog image |
| `source-diff-552-to-b2bee1d.txt` | `e9f55f06c57dd0d91f969f5d8cf8f92490d390c8aa42bafe29e0eebe31e1f937` | Same | MATCH | Source comparison snapshot |
| `stores-db-readonly.txt` | `ecf524ac5954f11ef9e3baf4eb96fc14188534369d7b298838eaf34594acd5fa` | Same | MATCH | Read-only Store snapshot |
| `test-summary.json` | `c7271a0d9e550adb021bc356735b03fed15d39582d556586c41bc107bc235bd6` | Same | MATCH | Historical summary plus corrected figure crosswalk |

<badge color="success">16/16 MATCH</badge> No missing or mismatched files. The two historical JDK/Gradle version log paths and stale README/uat-matrix/github-status entries were removed from the manifest because those files are absent and the editable Markdown is not immutable payload. JDK/Gradle versions remain explicitly described as recorded metadata, not raw version captures.

`SHA256SUMS.txt` is text with LF and diff enabled. Logs, JSON, text snapshots and screenshots are byte-stable with `-text -diff`. The manifest excludes itself and `.gitattributes`.
