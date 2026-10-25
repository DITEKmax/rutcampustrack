"""Exact served Vue WEB artifact oracle. Reads the deployed revision; never deploys."""
import hashlib
import json
import re
import subprocess
import sys
import urllib.parse
import urllib.request
from html.parser import HTMLParser


class OracleFailure(Exception):
    pass


def require(value):
    if not value:
        raise OracleFailure()


class Entrypoint(HTMLParser):
    def __init__(self):
        super().__init__()
        self.modules = []
        self.roots = 0

    def handle_starttag(self, tag, attrs):
        attributes = dict(attrs)
        self.roots += attributes.get('id') == 'app'
        if tag == 'script' and attributes.get('type', '').lower() == 'module':
            self.modules.append(attributes.get('src', ''))


def entry_asset(html):
    parser = Entrypoint()
    try:
        parser.feed(html.decode('utf-8'))
    except UnicodeDecodeError as error:
        raise OracleFailure() from error
    require(parser.roots == 1 and len(parser.modules) == 1)
    asset = parser.modules[0]
    # Root Vite output only: no remote URL, query, fragment or nested/traversal path.
    require(re.fullmatch(r'/assets/[A-Za-z0-9_][A-Za-z0-9_.-]*-[A-Za-z0-9_-]{8,}\.js', asset))
    return asset


def verify(expected_index, expected_asset, served_index, served_asset):
    # Status/MIME and exact bytes bind both resources to the same revision artifact.
    status, mime, html = served_index
    require(status == 200 and mime.split(';')[0].strip().lower() == 'text/html')
    require(hashlib.sha256(html).digest() == hashlib.sha256(expected_index).digest())
    asset_path = entry_asset(html)
    status, mime, asset = served_asset
    require(status == 200 and mime.split(';')[0].strip().lower() in ('application/javascript', 'text/javascript'))
    require(bool(asset) and hashlib.sha256(asset).digest() == hashlib.sha256(expected_asset).digest())
    return asset_path


def command(*args):
    result = subprocess.run(args, check=True, capture_output=True, timeout=10)
    require(len(result.stdout) <= 32 * 1024 * 1024)
    return result.stdout


def fetch(url):
    with urllib.request.urlopen(url, timeout=10) as response:
        body = response.read(32 * 1024 * 1024 + 1)
        require(len(body) <= 32 * 1024 * 1024)
        return response.status, response.headers.get('Content-Type', ''), body


def main(base, revision):
    require(re.fullmatch(r'[0-9a-f]{40}', revision))
    parsed = urllib.parse.urlsplit(base)
    require(parsed.scheme in ('http', 'https') and parsed.netloc and not parsed.username and not parsed.password)
    require(parsed.path in ('', '/') and not parsed.query and not parsed.fragment)
    image = command('docker', 'inspect', '--format', '{{.Config.Image}}', 'rct-web-panel-nginx').decode().strip()
    require(image == 'ghcr.io/ditekmax/rutcampustrack/web-panel-nginx:' + revision)
    expected_index = command('docker', 'exec', 'rct-web-panel-nginx', 'cat', '/usr/share/nginx/html/index.html')
    asset = entry_asset(expected_index).lstrip('/')
    expected_asset = command('docker', 'exec', 'rct-web-panel-nginx', 'cat', '/usr/share/nginx/html/' + asset)
    verify(expected_index, expected_asset, fetch(base.rstrip('/') + '/login'), fetch(base.rstrip('/') + '/' + asset))


if __name__ == '__main__':
    try:
        require(len(sys.argv) == 3)
        main(sys.argv[1], sys.argv[2])
    except Exception:
        print(json.dumps({'state': 'FAIL', 'boundary': 'SERVED_WEB_PANEL_ARTIFACT'}))
        sys.exit(1)
    print(json.dumps({'state': 'PASS', 'boundary': 'SERVED_WEB_PANEL_ARTIFACT'}))
