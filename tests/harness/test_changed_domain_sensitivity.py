"""Exercise the actual sensitivity admission/mutation code without writes or Gradle."""
import ast
import os
from pathlib import Path
import re
import subprocess
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
SOURCE = (ROOT / 'scripts/local-release/changed-domain.sh').read_text()
PROGRAM = SOURCE.split("<<'PY_DOMAIN_RED'\n", 1)[1].rsplit('\nPY_DOMAIN_RED', 1)[0]
TREE = ast.parse(PROGRAM)
# Keep the real admission and mutation builder, stop before it moves old results,
# writes any counterexample, starts Gradle or processes a RED result.
PREFIX = TREE.body[:next(i for i, node in enumerate(TREE.body) if isinstance(node, ast.Try))]
TRY = next(node for node in TREE.body if isinstance(node, ast.Try))
BODY = TRY.body[:next(i for i, node in enumerate(TRY.body)
                     if isinstance(node, ast.For) and isinstance(node.target, ast.Tuple))]
ADMISSION = compile(ast.Module(body=PREFIX + BODY, type_ignores=[]), '<sensitivity-admission>', 'exec')
READ = Path.read_bytes
CHECK_OUTPUT = subprocess.check_output


class SensitivityAdmissionControls(unittest.TestCase):
    def admit(self, service, mutate=lambda path, data: data):
        def read(path):
            data = READ(path).replace(b'\r\n', b'\n')
            return mutate(path, data) if path.suffix in {'.java', '.sql'} else data

        def git(command, **kwargs):
            if command[:3] == ['git', 'show', 'HEAD:']:
                raise AssertionError('Expected a scoped source path')
            if command[:2] == ['git', 'show'] and command[2].startswith('HEAD:'):
                return read(Path(command[2][5:]))
            if command[:3] == ['git', 'status', '--porcelain']:
                return b''  # Simulate a committed source revision with the requested variation.
            return CHECK_OUTPUT(command, **kwargs)

        with tempfile.TemporaryDirectory(dir=ROOT / '.agent') as temporary, patch.dict(os.environ, {
            'RCT_PROBE_SERVICE': service, 'RUNNER_TEMP': temporary,
            'RCT_PRODUCER_HEAD_SHA': 'a' * 40, 'RCT_LOCAL_RELEASE_REVISION': 'a' * 40,
        }), patch.object(Path, 'read_bytes', read), patch.object(subprocess, 'check_output', git):
            scope = {}
            exec(ADMISSION, scope)
            self.assertTrue(scope['replacements'])
            for path, data in scope['replacements'].items():
                self.assertNotEqual(data, scope['source_snapshots'][path])
            return scope

    def test_each_domain_accepts_unrelated_java_edit_and_builds_real_counterexamples(self):
        def frames(proof):
            result = {}
            for key, value in proof.items():
                if isinstance(value, dict):
                    result.update({key + '.' + nested: frame for nested, frame in frames(value).items()})
                elif key in {'ruleFrame', 'helperFrame'}:
                    result[key] = value
            return result

        for service in ('schedule-service', 'attendance-service', 'academic-service',
                        'notification-service', 'auth-service'):
            with self.subTest(service=service):
                original_frames = frames(self.admit(service)['proof'])
                scope = self.admit(service, lambda path, data: b'// unrelated committed edit\n' + data
                                   if path.suffix == '.java' else data)
                self.assertTrue(scope['proof']['counterexample'])
                shifted_frames = frames(scope['proof'])
                self.assertTrue(original_frames)
                self.assertEqual(original_frames.keys(), shifted_frames.keys())
                for key, frame in original_frames.items():
                    match = re.fullmatch(r'(.*\.java:)([1-9][0-9]*)\)', frame)
                    self.assertIsNotNone(match)
                    self.assertEqual(shifted_frames[key], match[1] + str(int(match[2]) + 1) + ')')

    def test_missing_or_ambiguous_mutation_anchor_is_refused(self):
        anchor = b'long increased = (long) before.limit() + command.increase();'
        for replacement in (b'long increased = command.increase();', anchor + anchor):
            with self.subTest(replacement=replacement):
                with self.assertRaises(AssertionError):
                    self.admit('attendance-service', lambda path, data: data.replace(anchor, replacement))

    def test_migration_integrity_is_still_enforced(self):
        with self.assertRaises(AssertionError):
            self.admit('schedule-service', lambda path, data: data + b'\n-- altered migration\n'
                       if path.suffix == '.sql' else data)


if __name__ == '__main__':
    os.chdir(ROOT)
    unittest.main()
