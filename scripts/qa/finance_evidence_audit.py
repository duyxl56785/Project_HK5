"""Read-only, offline Finance QA evidence audit. Never grants approval or changes flags.

Artifact integrity is not producer authentication, a business signature, PostgreSQL
runtime proof, or live E2E. Even complete references yield owner acceptance review.
"""
import argparse
from collections import Counter
import datetime as dt
import hashlib
import json
from pathlib import Path
import re
from urllib.parse import urlsplit
import xml.etree.ElementTree as ET

GATES = ('finance_policy', 'cod_source_and_authority', 'settlement_contract',
         'reconciliation_contract', 'store_authorization', 'runtime_environment')
LEVELS = {'UNIT', 'POSTGRESQL', 'LOCAL_INTEGRATION', 'LIVE_E2E'}
SHA = re.compile(r'[a-f0-9]{40}')

def _inside(root, relative):
    if not isinstance(relative, str) or Path(relative).is_absolute():
        raise ValueError('Invalid artifact path')
    base = Path(root).resolve(strict=True)
    path = (base / relative).resolve(strict=True)
    path.relative_to(base)  # also rejects symlink escapes before opening a file
    if not path.is_file():
        raise ValueError('Artifact must be a file')
    return path

def _counts(value, depth=0):
    result = Counter(PASS=0, FAIL=0, SKIP=0)
    if not isinstance(value, dict) or depth > 2:
        raise ValueError('Invalid counts')
    for key, count in value.items():
        if isinstance(count, dict):
            result.update(_counts(count, depth + 1))
        else:
            key = 'SKIP' if key == 'SKIPPED' else key
            if key not in result or type(count) is not int or count < 0:
                raise ValueError('Invalid counts')
            result[key] += count
    return result

def _reference(value):
    if not isinstance(value, str):
        return False
    try:
        u = urlsplit(value)
        return (u.scheme == 'https' and u.netloc == 'github.com' and not u.query
                and re.match(r'^/duyxl56785/Project_HK5/(pull|issues|blob|commit|actions)/', u.path) is not None)
    except ValueError:
        return False

def _xml_cases(data):
    if len(data) > 8 * 1024 * 1024 or b'<!DOCTYPE' in data.upper() or b'<!ENTITY' in data.upper():
        raise ValueError('Unsafe XML')
    counts, environment_failures = Counter(PASS=0, FAIL=0, SKIP=0), 0
    for case in ET.fromstring(data).iter('testcase'):
        problem = case.find('failure')
        if problem is None:
            problem = case.find('error')
        state = 'FAIL' if problem is not None else 'SKIP' if case.find('skipped') is not None else 'PASS'
        counts[state] += 1
        if problem is not None:
            text = ''.join(problem.itertext()).lower()
            environment_failures += int(case.get('name') == 'initializationError' and 'docker' in text)
    return counts, environment_failures

def audit_run(receipt, evidence_root, *, expected_head, level, uses_mocks):
    result = {'head': expected_head if isinstance(expected_head, str) and SHA.fullmatch(expected_head) else None,
              'level': level if level in LEVELS else 'UNKNOWN', 'status': 'INVALID_EVIDENCE',
              'counts': None, 'artifactCount': 0, 'executionClaimsOnly': True}
    try:
        if not isinstance(receipt, dict) or not isinstance(expected_head, str) or not SHA.fullmatch(expected_head):
            return result
        if receipt.get('head') != expected_head:
            return {**result, 'status': 'STALE_EVIDENCE'}
        if level not in LEVELS or type(uses_mocks) is not bool:
            return {**result, 'status': 'INVALID_SCOPE'}
        if level == 'LIVE_E2E' and (uses_mocks or receipt.get('executionLevel') != 'LIVE_E2E' or receipt.get('usesMocks') is not False):
            return {**result, 'status': 'INVALID_SCOPE'}
        start = dt.datetime.fromisoformat(receipt['startedUtc'])
        end = dt.datetime.fromisoformat(receipt['completedUtc'])
        if start.utcoffset() != dt.timedelta(0) or end.utcoffset() != dt.timedelta(0) or end < start or type(receipt.get('exitCode')) is not int:
            return result
        artifacts = receipt.get('rawArtifacts')
        if not isinstance(artifacts, list) or len(artifacts) > 500:
            return result
        actual = Counter(PASS=0, FAIL=0, SKIP=0)
        xml_count, environment_failures, seen = 0, 0, set()
        for item in artifacts:
            path = _inside(evidence_root, item['localPath'])
            if path in seen or path.stat().st_size > 64 * 1024 * 1024:
                return result
            seen.add(path)
            data = path.read_bytes()
            if len(data) != item['bytes'] or hashlib.sha256(data).hexdigest() != item['sha256']:
                return result
            if path.suffix.lower() != '.xml':
                continue
            xml_count += 1
            counts, blocked = _xml_cases(data)
            actual.update(counts)
            environment_failures += blocked
        result.update(artifactCount=len(seen), counts=dict(actual))
        if not xml_count:
            return {**result, 'status': 'NOT_RUN'}
        if actual != _counts(receipt.get('counts')):
            return {**result, 'status': 'INVALID_EVIDENCE'}
        # An infrastructure error must not hide another executed test failure,
        # whether both are in one XML suite or in separate artifact files.
        if actual['FAIL'] > environment_failures:
            return {**result, 'status': 'FAIL'}
        if environment_failures:
            return {**result, 'status': 'BLOCKED_ENVIRONMENT'}
        if receipt['exitCode'] != 0:
            return {**result, 'status': 'BLOCKED_EXECUTION'}
        return {**result, 'status': 'VERIFIED_ARTIFACTS' if actual['PASS'] else 'NOT_RUN'}
    except (OSError, ValueError, TypeError, KeyError, ET.ParseError):
        # Do not echo arbitrary paths, logs, XML, commands, errors or credentials.
        return {**result, 'status': 'INVALID_EVIDENCE'}

def assess(manifest, evidence_root):
    result = {'kind': 'OFFLINE_QA_ARTIFACT_AUDIT_NOT_FINANCE_ACCEPTANCE', 'status': 'BLOCKED_DEPENDENCY',
              'runs': [], 'gates': {}, 'blockers': [], 'releaseTotal': None,
              'authority': 'No business approval, runtime activation, DONE, merge or Finance flag is granted by this tool.'}
    if not isinstance(manifest, dict):
        result['blockers'].append('INVALID_MANIFEST')
        return result
    gates = manifest.get('gates', {})
    for name in GATES:
        item = gates.get(name) if isinstance(gates, dict) else None
        provided = isinstance(item, dict) and item.get('state') == 'REFERENCE_PROVIDED' and _reference(item.get('reference'))
        result['gates'][name] = 'REFERENCE_REQUIRES_HUMAN_VERIFICATION' if provided else 'PENDING'
        if not provided:
            result['blockers'].append('MISSING_' + name.upper())
    runs = manifest.get('runs')
    if not isinstance(runs, list) or len(runs) > 100:
        result['blockers'].append('INVALID_RUNS')
        return result
    live = False
    for spec in runs:
        try:
            path = _inside(evidence_root, spec['receipt'])
            if path.stat().st_size > 8 * 1024 * 1024:
                raise ValueError('Receipt too large')
            receipt = json.loads(path.read_text(encoding='utf-8'))
            row = audit_run(receipt, evidence_root, expected_head=spec['head'], level=spec['level'], uses_mocks=spec['usesMocks'])
            result['runs'].append(row)
            if row['status'] != 'VERIFIED_ARTIFACTS':
                result['blockers'].append('RUN_' + row['status'])
            live |= row['status'] == 'VERIFIED_ARTIFACTS' and row['level'] == 'LIVE_E2E'
        except (OSError, KeyError, TypeError, ValueError):
            result['blockers'].append('INVALID_RECEIPT')
    if not live:
        result['blockers'].append('LIVE_E2E_NOT_VERIFIED')
    result['blockers'] = sorted(set(result['blockers']))
    if not result['blockers']:
        result['status'] = 'WAITING_OWNER_ACCEPTANCE'  # references and producer claims still need human verification
    return result

def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--manifest', type=Path, required=True)
    parser.add_argument('--evidence-root', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args(argv)
    try:
        manifest = json.loads(args.manifest.read_text(encoding='utf-8'))
        result = assess(manifest, args.evidence_root)
        # Exclusive creation preserves historical evidence, including RED.
        with args.output.open('x', encoding='utf-8') as stream:
            json.dump(result, stream, ensure_ascii=False, indent=2)
            stream.write('\n')
        print(result['status'] + '; releaseTotal=null; owner verification required')
        return 2 if result['status'] == 'BLOCKED_DEPENDENCY' else 0
    except (OSError, ValueError, TypeError):
        print('Audit unavailable: invalid input or output exists; no acceptance inferred')
        return 1

if __name__ == '__main__':
    raise SystemExit(main())
