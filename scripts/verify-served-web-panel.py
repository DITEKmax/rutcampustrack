"""Exact served web-panel artifact oracle. Reads the already deployed revision; never deploys."""
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
        self.scripts = []
        self.angular = False

    def handle_starttag(self, tag, attrs):
        attributes = dict(attrs)
        self.angular |= tag == 'app-root'
        if tag == 'script' and attributes.get('src'):
            self.scripts.append(attributes['src'])


def verify(expected_index, expected_asset, served_index, served_asset):
    # Status/MIME and exact bytes bind both resources to the same revision artifact.
    status, mime, html = served_index
    require(status == 200 and mime.split(';')[0].strip().lower() == 'text/html')
    require(hashlib.sha256(html).digest() == hashlib.sha256(expected_index).digest())
    parser = Entrypoint()
    parser.feed(html.decode('utf-8'))
    require(parser.angular)
    scripts = [path for path in parser.scripts if re.fullmatch(r'/?main[-.][A-Za-z0-9_-]+\.js', path)]
    require(len(scripts) == 1)
    status, mime, asset = served_asset
    require(status == 200 and mime.split(';')[0].strip().lower() in ('application/javascript', 'text/javascript'))
    require(bool(asset) and hashlib.sha256(asset).digest() == hashlib.sha256(expected_asset).digest())
    return scripts[0]


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
    image = command('docker', 'inspect', '--format', '{{.Config.Image}}', 'rct-web-panel-nginx').decode().strip()
    require(image == 'ghcr.io/ditekmax/rutcampustrack/web-panel-nginx:' + revision)
    expected_index = command('docker', 'exec', 'rct-web-panel-nginx', 'cat', '/usr/share/nginx/html/index.html')
    parser = Entrypoint()
    parser.feed(expected_index.decode('utf-8'))
    assets = [src for src in parser.scripts if re.fullmatch(r'/?main[-.][A-Za-z0-9_-]+\.js', src)]
    require(len(assets) == 1)
    asset = assets[0].lstrip('/')
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
