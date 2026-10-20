"""Browser-free controls of the actual served-artifact oracle; no Docker or remote HTTP."""
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('served_web_panel', Path(__file__).parents[2] / 'scripts/verify-served-web-panel.py')
oracle = importlib.util.module_from_spec(spec)
spec.loader.exec_module(oracle)


class ServedArtifactControl(unittest.TestCase):
    def test_small_exact_revision_index_and_asset_pass(self):
        html = b'<!doctype html><app-root></app-root><script src="main-OWNED.js"></script>'
        self.assertEqual(oracle.verify(html, b'owned js', (200, 'text/html; charset=utf-8', html),
                                      (200, 'application/javascript', b'owned js')), 'main-OWNED.js')

    def test_wrong_spa_error_mime_revision_and_asset_fail(self):
        html = b'<!doctype html><app-root></app-root><script src="main-OWNED.js"></script>'
        for index, asset in [
            ((500, 'text/html', b'x' * 6000), (200, 'application/javascript', b'owned js')),
            ((200, 'text/html', b'<other-spa>' + b'x' * 6000), (200, 'application/javascript', b'owned js')),
            ((200, 'text/plain', html), (200, 'application/javascript', b'owned js')),
            ((200, 'text/html', html), (404, 'application/javascript', b'owned js')),
            ((200, 'text/html', html), (200, 'text/html', b'owned js')),
            ((200, 'text/html', html), (200, 'application/javascript', b'foreign js')),
        ]:
            with self.assertRaises(oracle.OracleFailure):
                oracle.verify(html, b'owned js', index, asset)


if __name__ == '__main__':
    unittest.main()
