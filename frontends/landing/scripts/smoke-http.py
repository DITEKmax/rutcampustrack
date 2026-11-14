"""Check the built landing via its Nginx server or the public reverse proxy."""

import argparse
import gzip
import re
from urllib.error import HTTPError
from urllib.request import HTTPRedirectHandler, Request, build_opener


class KeepRedirects(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def expect(condition, path, detail):
    if not condition:
        raise SystemExit(f'{path}: {detail}')


def smoke(origin, prefix):
    client = build_opener(KeepRedirects())

    def get(path, status=200, **headers):
        request = Request(origin + path, headers=headers)
        try:
            response = client.open(request, timeout=10)
        except HTTPError as error:
            response = error
        with response:
            expect(response.code == status, path, f'expected HTTP {status}, got {response.code}')
            return response.headers, response.read()

    def representation(path, mime, cache, *, compressed=False, revalidate=False, noindex=False):
        identity, body = get(path)
        expect(identity.get_content_type() == mime, path, f'expected MIME {mime}')
        if revalidate or mime == 'application/javascript':
            expect(identity.get_content_charset() == 'utf-8', path, 'expected UTF-8')
        expect(identity.get('Cache-Control') == cache, path, f'expected Cache-Control: {cache}')
        if noindex:
            expect(identity.get('X-Robots-Tag') == 'noindex', path, 'expected X-Robots-Tag: noindex')
        variants = [(identity, {})]
        if compressed:
            encoding = {'Accept-Encoding': 'gzip'}
            zipped, compressed_body = get(path, **encoding)
            expect(zipped.get('Content-Encoding') == 'gzip', path, 'expected gzip')
            expect(identity.get('Content-Encoding') is None, path, 'expected identity encoding')
            for headers in (identity, zipped):
                vary = ','.join(headers.get_all('Vary', [])).lower().replace(' ', '').split(',')
                expect('accept-encoding' in vary, path, 'expected Vary: Accept-Encoding')
            expect(gzip.decompress(compressed_body) == body, path, 'gzip and identity bodies differ')
            expect(zipped.get('Last-Modified') == identity.get('Last-Modified'), path,
                   'gzip and identity modification times differ')
            variants.append((zipped, encoding))
        if revalidate:
            for headers, encoding in variants:
                etag = headers.get('ETag')
                expect(etag, path, 'missing ETag')
                unchanged, unchanged_body = get(path, 304, **encoding, **{'If-None-Match': etag})
                expect(not unchanged_body and unchanged.get('Cache-Control') == cache,
                       path, 'expected an empty 304 with the same cache policy')
        return body

    html = representation(prefix + '/', 'text/html', 'no-cache', compressed=True, revalidate=True)
    demo = representation(prefix + '/demo-pwa.html', 'text/html', 'no-cache', revalidate=True)
    expect(b'Content-Security-Policy' in demo, prefix + '/demo-pwa.html', 'missing demo CSP')
    representation(prefix + '/schedule/', 'text/html', 'no-cache', revalidate=True)
    redirect, _ = get(prefix + '/schedule?landing=1', 301)
    expect(redirect.get('Location') == prefix + '/schedule/?landing=1', prefix + '/schedule',
           'expected a canonical redirect with the query intact')
    representation('/robots.txt', 'text/plain', 'no-cache', revalidate=True)
    representation('/llms.txt', 'text/plain', 'no-cache', compressed=True, revalidate=True)
    representation('/sitemap.xml', 'application/xml', 'no-cache', revalidate=True)
    representation(prefix + '/schedule/index.md', 'text/markdown', 'no-cache',
                   compressed=True, revalidate=True, noindex=True)

    asset = re.search(rb'<script\b[^>]*src="/presentation(/assets/[^"/]+-[\w-]{8}\.js)"', html)
    expect(asset is not None, prefix + '/', 'no hashed presentation script in HTML')
    asset_path = prefix + asset.group(1).decode('ascii')
    representation(asset_path, 'application/javascript', 'public, max-age=31536000, immutable', compressed=True)
    representation(prefix + '/brand-20b-g08/favicon.svg', 'image/svg+xml', 'public, max-age=300')
    for path in (prefix + '/assets/missing-AbCdEf01.js', prefix + '/does-not-exist/', asset_path + '.gz'):
        headers, _ = get(path, 404)
        expect('immutable' not in headers.get('Cache-Control', ''), path, '404 is immutable')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('origin', help='HTTP(S) origin, for example http://127.0.0.1:14315')
    parser.add_argument('--edge', action='store_true', help='check the reverse proxy with /presentation/ routing')
    args = parser.parse_args()
    smoke(args.origin.rstrip('/'), '/presentation' if args.edge else '')
    print('Landing HTTP smoke passed')
