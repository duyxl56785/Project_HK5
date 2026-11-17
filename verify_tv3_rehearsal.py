"""Read-only verifier for a private TV3 Checkout rehearsal evidence directory."""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import sys
from uuid import UUID

HEX40 = re.compile(r"[0-9a-f]{40}")
CHECKSUM = re.compile(r"([0-9a-f]{64})  (.+)")
IMAGES = ("Hinh_6_5_Cart_2_Store_REHEARSAL.png", "Hinh_6_6_1_Root_2_SellerOrders_REHEARSAL.png")


def require(condition, label):
    if not condition:
        raise ValueError(label)


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def uuid(value):
    try:
        return isinstance(value, str) and str(UUID(value)) == value.lower()
    except (TypeError, ValueError):
        return False


def verify_manifest(root):
    manifest = root / "SHA256SUMS.txt"
    require(not manifest.is_symlink(), "Checksum manifest must be a regular file")
    seen = set()
    for raw in manifest.read_text(encoding="utf-8-sig").splitlines():
        match = CHECKSUM.fullmatch(raw)
        require(match, "Malformed manifest line")
        expected, name = match.groups()
        relative = PurePosixPath(name)
        require(not relative.is_absolute() and relative.as_posix() == name and
                ".." not in relative.parts and ":" not in name and "\\" not in name and name not in seen,
                "Invalid or duplicate path in checksum manifest")
        path = root / relative
        require(path.resolve(strict=True).is_relative_to(root) and path.is_file() and not path.is_symlink(),
                "Evidence path outside root or not regular")
        with path.open("rb") as stream:
            actual = hashlib.file_digest(stream, "sha256").hexdigest()
        require(actual == expected, "SHA256 mismatch: " + name)
        seen.add(name)
    actual_files = {p.relative_to(root).as_posix() for p in root.rglob("*")
                    if p.is_file() and p != manifest}
    require(seen == actual_files and "run.json" in seen, "Missing or unlisted evidence file")
    return seen


def balance_rows(rows, with_reserved):
    require(isinstance(rows, list) and len(rows) == 2, "Expected two SKU balances")
    result = {}
    for item in rows:
        sku = item["sku"]
        values = (item["available"], item["reserved"]) if with_reserved else (item["available"],)
        require(isinstance(sku, str) and sku.strip() and sku not in result and
                all(type(value) is int and value >= 0 for value in values), "Invalid SKU balance")
        result[sku] = values
    return result


def verify(root, sha):
    require(HEX40.fullmatch(sha), "Expected a full 40-character SHA")
    names = verify_manifest(root)
    run = read_json(root / "run.json")
    require(run["kind"] == "TV3_CHECKOUT_REHEARSAL_RUN" and run["sourceSha"] == sha, "Run kind or SHA mismatch")
    if run.get("status") not in {"PASS", "PARTIAL"}:
        require(run.get("status") in {"FAIL", "NOT_RUN", "NOT_RUN_LOCAL"}, "Invalid run status")
        return {"status": run["status"], "integrity": "PASS", "files": len(names)}
    require(run.get("exitCode") == 0 and run.get("playwrightExitCode") == 0 and run.get("phase") == "complete",
            "PASS run is missing successful completion")
    require(set(IMAGES) | {"receipt.json", "playwright-results.json", "playwright.log"} <= names,
            "Missing receipt, report, log or PNG")
    for image in IMAGES:
        with (root / image).open("rb") as stream:
            require(stream.read(8) == b"\x89PNG\r\n\x1a\n", "Invalid PNG signature: " + image)
    stats = read_json(root / "playwright-results.json")["stats"]
    require(stats["expected"] == 1 and stats["unexpected"] == stats["skipped"] == stats["flaky"] == 0,
            "Playwright report did not execute exactly one clean test")

    proof = read_json(root / "receipt.json")
    require(proof["kind"] == "TV3_CHECKOUT_REHEARSAL" and proof["sourceSha"] == sha and
            proof["project"] == run["project"] and proof["status"] == run["status"], "Receipt kind, SHA, project or status mismatch")
    order = proof["orderId"]
    children = proof["storeOrders"]
    require(uuid(order) and proof["paymentMethod"] == "COD" and proof["rootCount"] == 1 and
            proof["customerOrderDelta"] == 1 and isinstance(children, list) and len(children) == 2, "Invalid root Order or COD")
    require(all(uuid(c["id"]) and c["orderId"] == order and uuid(c["storeId"]) for c in children) and
            len({c["id"] for c in children}) == len({c["storeId"] for c in children}) == 2, "Invalid SellerOrders")
    use_postgres = proof["status"] == "PASS"
    scope = "POSTGRES_READ_ONLY" if use_postgres else "CATALOG_API_ONLY"
    stock = proof["stock"]
    require(stock.get("scope", "POSTGRES_READ_ONLY") == scope and run.get("proofScope", "POSTGRES_READ_ONLY") == scope,
            "Stock scope does not match status")
    replay = proof["replay"]
    require(replay["status"] == 200 and replay["orderId"] == order and replay["mismatchStatus"] == 409 and
            replay["reserveOperationCount"] == (1 if use_postgres else None), "Replay did not prove same Order / rejection / single reserve")
    ownership = proof["ownership"]
    sellers = ownership["sellers"]
    require(ownership["anonymousStatus"] == 401 and isinstance(sellers, list) and len(sellers) == 2 and
            {seller["ownSellerOrderId"] for seller in sellers} == {child["id"] for child in children}, "Invalid actor coverage")
    require(all(s["ownStatus"] == 200 and s["ownListed"] is True and s["otherListed"] is False and
                s["crossStatus"] == 404 and s["customerRootStatus"] in {403, 404} for s in sellers), "Ownership denial missing")

    before = balance_rows(stock["before"], use_postgres)
    after = balance_rows(stock["afterCheckout"], use_postgres)
    after_replay = balance_rows(stock["afterReplay"], use_postgres)
    expected = {sku: (amounts[0] - 1, amounts[1] + 1) if use_postgres else (amounts[0] - 1,)
                for sku, amounts in before.items()}
    require(after == expected and after_replay == after, "Stock delta or replay balance mismatch")
    return {"status": proof["status"], "scope": scope, "integrity": "PASS", "files": len(names), "sourceSha": sha}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("evidence", type=Path)
    parser.add_argument("--expected-sha", required=True)
    args = parser.parse_args()
    try:
        result = verify(args.evidence.resolve(strict=True), args.expected_sha)
    except (OSError, KeyError, TypeError, ValueError, AttributeError) as error:
        reason = str(error) if type(error) is ValueError else "Evidence missing or malformed"
        print(json.dumps({"status": "FAIL", "integrity": "FAIL", "reason": reason}))
        return 1
    print(json.dumps(result))
    return 0 if result["status"] == "PASS" else 2


if __name__ == "__main__":
    sys.exit(main())
