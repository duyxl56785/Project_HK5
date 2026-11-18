# Evidence manifest audit

Receipt: `reports/minh-chung-kiem-thu/tv5-prefinal-552e8eb-20261115/`.

| Payload group | Count | Verification |
|---|---:|---|
| Backend raw logs | 6 | Present; working bytes match manifest |
| Frontend raw logs | 4 | Present; working bytes match manifest |
| Read-only data / source snapshot text | 3 | Present; working bytes match manifest |
| JSON snapshots | 2 | Present; JSON parse PASS and hashes match |
| Screenshot | 1 | Present; hash `66510614854578543463737fbb653e630396208b872728041693c85269c03811` |
| **Total** | **16** | **16/16 working-tree SHA256 matches** |

Manifest contains 16 relative paths, one per payload file. It excludes itself, `.gitattributes` and editable Markdown. After payload commit, recompute from `git cat-file blob <payload-commit>:<path>`, commit the LF text manifest separately, then verify all 16 blobs again at final HEAD.
