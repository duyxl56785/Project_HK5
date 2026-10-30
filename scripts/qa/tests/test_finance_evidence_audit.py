import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from finance_evidence_audit import audit_run, assess

HEAD = 'a' * 40
OTHER = 'b' * 40
GATES = ['finance_policy', 'cod_source_and_authority', 'settlement_contract', 'reconciliation_contract', 'store_authorization', 'runtime_environment']

class FinanceEvidenceAuditTest(unittest.TestCase):
    """Synthetic artifact fixtures test the auditor, never evidence of live Finance."""
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.xml = self.root / 'TEST-local.xml'
        self.xml.write_text('<testsuite><testcase classname="Synthetic" name="pass"/><testcase classname="Synthetic" name="not-executed"><skipped/></testcase></testsuite>', encoding='utf-8')
        self.log = self.root / 'runtime.log'
        self.log.write_bytes(b'SYNTHETIC audit unit fixture only\r\n')
        self.receipt = {'head': HEAD, 'startedUtc': '2026-10-27T00:00:00+00:00', 'completedUtc': '2026-10-27T00:01:00+00:00',
                        'exitCode': 0, 'counts': {'PASS': 1, 'SKIP': 1}, 'rawArtifacts': [self.artifact(self.log), self.artifact(self.xml)]}

    def artifact(self, p):
        return {'localPath': p.relative_to(self.root).as_posix(), 'bytes': p.stat().st_size, 'sha256': hashlib.sha256(p.read_bytes()).hexdigest()}

    def run_audit(self, **kwargs):
        return audit_run(self.receipt, self.root, expected_head=kwargs.get('head', HEAD), level=kwargs.get('level', 'POSTGRESQL'), uses_mocks=kwargs.get('mocks', True))

    def save(self):
        p = self.root / 'receipt.json'; p.write_text(json.dumps(self.receipt), encoding='utf-8'); return p

    def manifest(self):
        return {'gates': {key: {'state': 'PENDING', 'reference': None} for key in GATES},
                'runs': [{'receipt': self.save().name, 'head': HEAD, 'level': 'POSTGRESQL', 'usesMocks': True}]}

    def test_real_xml_counts_and_hashes_match_without_reading_test_names_as_policy(self):
        result = self.run_audit()
        self.assertEqual(result['status'], 'VERIFIED_ARTIFACTS')
        self.assertEqual(result['counts'], {'PASS': 1, 'FAIL': 0, 'SKIP': 1})

    def test_changed_raw_bytes_cannot_retain_pass(self):
        self.log.write_bytes(b'tampered')
        self.assertEqual(self.run_audit()['status'], 'INVALID_EVIDENCE')

    def test_claimed_pass_count_must_match_xml(self):
        self.receipt['counts']['PASS'] = 999
        self.assertEqual(self.run_audit()['status'], 'INVALID_EVIDENCE')

    def test_old_head_is_stale_not_current_pass(self):
        self.assertEqual(self.run_audit(head=OTHER)['status'], 'STALE_EVIDENCE')

    def test_traversal_is_rejected_before_reading_outside_root(self):
        self.receipt['rawArtifacts'][0]['localPath'] = '../outside.log'
        self.assertEqual(self.run_audit()['status'], 'INVALID_EVIDENCE')

    def test_missing_raw_log_is_not_a_verified_execution(self):
        self.log.unlink()
        self.assertEqual(self.run_audit()['status'], 'INVALID_EVIDENCE')

    def test_no_xml_cannot_be_called_pass(self):
        self.receipt['rawArtifacts'] = [self.artifact(self.log)]
        self.assertEqual(self.run_audit()['status'], 'NOT_RUN')

    def test_skipped_only_xml_is_not_an_executed_pass(self):
        self.xml.write_text('<testsuite><testcase name="skip"><skipped/></testcase></testsuite>', encoding='utf-8')
        self.receipt['rawArtifacts'][1] = self.artifact(self.xml)
        self.receipt['counts'] = {'SKIP': 1}
        self.assertEqual(self.run_audit()['status'], 'NOT_RUN')

    def test_unit_or_mock_receipt_never_proves_live_e2e(self):
        self.assertEqual(self.run_audit(level='LIVE_E2E', mocks=True)['status'], 'INVALID_SCOPE')
        result = assess(self.manifest(), self.root)
        self.assertEqual(result['status'], 'BLOCKED_DEPENDENCY')
        self.assertIn('LIVE_E2E_NOT_VERIFIED', result['blockers'])

    def test_pending_business_gates_stay_blocked_despite_green_postgres(self):
        result = assess(self.manifest(), self.root)
        self.assertEqual(result['status'], 'BLOCKED_DEPENDENCY')
        self.assertEqual(result['runs'][0]['status'], 'VERIFIED_ARTIFACTS')
        self.assertIsNone(result['releaseTotal'])

    def test_local_approved_string_is_not_a_human_signature(self):
        manifest = self.manifest()
        for gate in manifest['gates'].values():
            gate.update(state='APPROVED', reference='https://github.com/duyxl56785/Project_HK5/pull/84')
        self.assertEqual(assess(manifest, self.root)['status'], 'BLOCKED_DEPENDENCY')

    def test_mock_receipt_does_not_become_live_when_references_are_present(self):
        manifest = self.manifest()
        for gate in manifest['gates'].values():
            gate.update(state='REFERENCE_PROVIDED', reference='https://github.com/duyxl56785/Project_HK5/pull/84')
        result = assess(manifest, self.root)
        self.assertEqual(result['status'], 'BLOCKED_DEPENDENCY')
        self.assertNotIn(result['status'], ['DONE', 'APPROVED'])

    def test_different_heads_have_separate_runs_no_release_sum(self):
        manifest = self.manifest()
        self.receipt['head'] = OTHER
        second = self.root/'other.json';second.write_text(json.dumps(self.receipt), encoding='utf-8')
        manifest['runs'].append({'receipt': second.name, 'head': OTHER, 'level': 'POSTGRESQL', 'usesMocks': True})
        result = assess(manifest, self.root)
        self.assertEqual([run['head'] for run in result['runs']], [HEAD, OTHER])
        self.assertIsNone(result['releaseTotal'])

    def test_artifact_output_never_serializes_command_credentials_or_failure_text(self):
        self.receipt['command'] = ['--password=PRIVATE_CANARY']
        self.receipt['boundary'] = 'PRIVATE_CANARY'
        self.assertNotIn('PRIVATE_CANARY', json.dumps(self.run_audit()))

    def test_environment_failure_is_not_a_product_assertion_failure(self):
        self.xml.write_text('<testsuite><testcase name="initializationError"><error type="DockerError">No valid Docker environment</error></testcase></testsuite>', encoding='utf-8')
        self.receipt.update(exitCode=1, counts={'FAIL': 1});self.receipt['rawArtifacts'][1] = self.artifact(self.xml)
        self.assertEqual(self.run_audit()['status'], 'BLOCKED_ENVIRONMENT')

    def test_assertion_failure_is_retained_as_fail(self):
        self.xml.write_text('<testsuite><testcase name="regression"><failure type="AssertionError">expected</failure></testcase></testsuite>', encoding='utf-8')
        self.receipt.update(exitCode=1, counts={'FAIL': 1});self.receipt['rawArtifacts'][1] = self.artifact(self.xml)
        self.assertEqual(self.run_audit()['status'], 'FAIL')

    def test_docker_initialization_does_not_hide_an_independent_assertion_failure(self):
        self.xml.write_text('<testsuite><testcase name="initializationError"><error>Docker unavailable</error></testcase>'
                            '<testcase name="settlement-regression"><failure type="AssertionError">incorrect balance</failure></testcase></testsuite>', encoding='utf-8')
        self.receipt.update(exitCode=1, counts={'FAIL': 2})
        self.receipt['rawArtifacts'][1] = self.artifact(self.xml)
        result = self.run_audit()
        self.assertEqual(result['status'], 'FAIL')
        self.assertEqual(result['counts']['FAIL'], 2)

    def test_environment_failure_in_one_xml_cannot_mask_failure_in_another(self):
        self.xml.write_text('<testsuite><testcase name="initializationError"><error>Docker unavailable</error></testcase></testsuite>', encoding='utf-8')
        other = self.root/'TEST-assertion.xml'
        other.write_text('<testsuite><testcase name="independent"><failure type="AssertionError">incorrect state</failure></testcase></testsuite>', encoding='utf-8')
        self.receipt.update(exitCode=1, counts={'FAIL': 2})
        self.receipt['rawArtifacts'][1] = self.artifact(self.xml)
        self.receipt['rawArtifacts'].append(self.artifact(other))
        self.assertEqual(self.run_audit()['status'], 'FAIL')

    def test_malformed_xml_and_doctype_are_rejected(self):
        for content in ['<broken', '<!DOCTYPE a [<!ENTITY x "x">]><testsuite/>']:
            self.xml.write_text(content, encoding='utf-8');self.receipt['rawArtifacts'][1] = self.artifact(self.xml)
            self.assertEqual(self.run_audit()['status'], 'INVALID_EVIDENCE')

    def test_utf16_little_endian_doctype_cannot_bypass_xml_guard(self):
        self.check_encoded_doctype('utf-16-le')

    def test_utf16_big_endian_doctype_cannot_bypass_xml_guard(self):
        self.check_encoded_doctype('utf-16-be')

    def check_encoded_doctype(self, encoding):
        content = '<?xml version="1.0" encoding="UTF-16"?><!DOCTYPE testsuite [<!ENTITY x "expanded">]><testsuite><testcase name="&x;"/></testsuite>'
        self.xml.write_bytes(content.encode(encoding))
        self.receipt['counts'] = {'PASS': 1}
        self.receipt['rawArtifacts'][1] = self.artifact(self.xml)
        self.assertEqual(self.run_audit()['status'], 'INVALID_EVIDENCE')

    def test_valid_utf16_junit_without_doctype_remains_usable(self):
        self.xml.write_bytes('<?xml version="1.0" encoding="UTF-16"?><testsuite><testcase name="valid"/></testsuite>'.encode('utf-16'))
        self.receipt['counts'] = {'PASS': 1}
        self.receipt['rawArtifacts'][1] = self.artifact(self.xml)
        self.assertEqual(self.run_audit()['status'], 'VERIFIED_ARTIFACTS')

    def test_non_utc_or_backwards_execution_time_is_invalid(self):
        self.receipt['completedUtc'] = '2026-10-26T00:00:00+00:00'
        self.assertEqual(self.run_audit()['status'], 'INVALID_EVIDENCE')

    def test_malformed_expected_head_is_rejected(self):
        self.assertEqual(self.run_audit(head='not-a-sha')['status'], 'INVALID_EVIDENCE')

if __name__ == '__main__':
    unittest.main()
