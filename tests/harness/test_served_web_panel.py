"""Browser-free controls of the actual served-artifact oracle; no Docker or remote HTTP."""
import importlib.util
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import threading
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('served_web_panel', Path(__file__).parents[2] / 'scripts/verify-served-web-panel.py')
oracle = importlib.util.module_from_spec(spec)
spec.loader.exec_module(oracle)

ENTRY = '/assets/index-C4j5K6_m.js'
HTML = ('<!doctype html><div id="app"></div>'
        '<script type="module" crossorigin src="' + ENTRY + '"></script>').encode()
ASSET = b'const mounted = "owned Vue revision";'
REVISION = 'a' * 40


class ServedArtifactControl(unittest.TestCase):
    def test_small_exact_revision_index_and_asset_pass(self):
        for mime in ('application/javascript', 'text/javascript; charset=utf-8'):
            self.assertEqual(oracle.verify(HTML, ASSET, (200, 'text/html; charset=utf-8', HTML),
                                          (200, mime, ASSET)), ENTRY)

    def test_wrong_spa_error_mime_revision_and_asset_fail(self):
        for index, asset in [
            ((500, 'text/html', b'x' * 6000), (200, 'application/javascript', ASSET)),
            ((200, 'text/html', b'<other-spa>' + b'x' * 6000), (200, 'application/javascript', ASSET)),
            ((200, 'text/plain', HTML), (200, 'application/javascript', ASSET)),
            ((200, 'text/html', HTML.replace(b'C4j5K6_m', b'F0reign1')), (200, 'application/javascript', ASSET)),
            ((200, 'text/html', HTML), (404, 'application/javascript', ASSET)),
            ((200, 'text/html', HTML), (200, 'text/html', ASSET)),
            ((200, 'text/html', HTML), (200, 'application/javascript', HTML)),
            ((200, 'text/html', HTML), (200, 'application/javascript', b'foreign js')),
            ((200, 'text/html', HTML), (200, 'application/javascript', b'')),
        ]:
            with self.subTest(index_status=index[0], asset_status=asset[0], asset_mime=asset[1]):
                with self.assertRaises(oracle.OracleFailure):
                    oracle.verify(HTML, ASSET, index, asset)

    def test_non_vue_ambiguous_or_unsafe_entry_artifacts_fail(self):
        for html in [
            b'<app-root></app-root><script src="main-OWNED.js"></script>',
            HTML.replace(b'<div id="app"></div>', b'<div id="other"></div>'),
            HTML + b'<div id="app"></div>',
            HTML + b'<span id="app"></span>',
            HTML + b'<script type="module" src="/assets/other-C4j5K6_m.js"></script>',
            HTML.replace(b'type="module"', b'type="text/javascript"'),
            HTML.replace(ENTRY.encode(), b'/assets/index.js'),
            HTML.replace(ENTRY.encode(), b'assets/index-C4j5K6_m.js'),
            HTML.replace(ENTRY.encode(), b'https://foreign.invalid/assets/index-C4j5K6_m.js'),
            HTML.replace(ENTRY.encode(), b'//foreign.invalid/assets/index-C4j5K6_m.js'),
            HTML.replace(ENTRY.encode(), b'/assets/../index-C4j5K6_m.js'),
            HTML.replace(ENTRY.encode(), ENTRY.encode() + b'?revision=other'),
        ]:
            with self.subTest(html=html):
                with self.assertRaises(oracle.OracleFailure):
                    oracle.verify(html, ASSET, (200, 'text/html', html), (200, 'application/javascript', ASSET))

    def test_empty_revision_asset_cannot_pass(self):
        with self.assertRaises(oracle.OracleFailure):
            oracle.verify(HTML, b'', (200, 'text/html', HTML), (200, 'application/javascript', b''))


class ServedHttpControl(unittest.TestCase):
    """Exercise main's HTTP/identity boundary using only loopback and in-memory image files."""

    @classmethod
    def setUpClass(cls):
        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                status, mime, body = cls.responses.get(self.path, (404, 'text/plain', b'missing'))
                self.send_response(status)
                self.send_header('Content-Type', mime)
                self.end_headers()
                self.wfile.write(body)

            def log_message(self, *_args):
                pass

        cls.server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()
        cls.base = 'http://127.0.0.1:' + str(cls.server.server_port)

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join(timeout=2)

    def setUp(self):
        type(self).responses = {'/login': (200, 'text/html', HTML), ENTRY: (200, 'application/javascript', ASSET)}

    def image_files(self, *args):
        if args == ('docker', 'inspect', '--format', '{{.Config.Image}}', 'rct-web-panel-nginx'):
            return ('ghcr.io/ditekmax/rutcampustrack/web-panel-nginx:' + self.image_revision).encode()
        files = {'/usr/share/nginx/html/index.html': HTML, '/usr/share/nginx/html' + ENTRY: ASSET}
        if args[:4] == ('docker', 'exec', 'rct-web-panel-nginx', 'cat') and len(args) == 5:
            return files[args[4]]
        raise AssertionError('Unexpected image read')

    def test_exact_image_revision_and_loopback_served_bytes_pass(self):
        self.image_revision = REVISION
        with patch.object(oracle, 'command', self.image_files):
            oracle.main(self.base, REVISION)

    def test_wrong_image_revision_fails_even_with_identical_http_bytes(self):
        self.image_revision = 'b' * 40
        with patch.object(oracle, 'command', self.image_files):
            with self.assertRaises(oracle.OracleFailure):
                oracle.main(self.base, REVISION)

    def test_http_spa_fallback_for_module_is_not_an_artifact_pass(self):
        self.image_revision = REVISION
        type(self).responses[ENTRY] = (200, 'text/html', HTML)
        with patch.object(oracle, 'command', self.image_files):
            with self.assertRaises(oracle.OracleFailure):
                oracle.main(self.base, REVISION)

    def test_non_root_base_and_invalid_revision_fail(self):
        for base, revision in [(self.base + '/app', REVISION), (self.base + '/?other=1', REVISION),
                               (self.base, 'not-a-revision')]:
            with self.subTest(base=base, revision=revision):
                with self.assertRaises(oracle.OracleFailure):
                    oracle.main(base, revision)


if __name__ == '__main__':
    unittest.main()
