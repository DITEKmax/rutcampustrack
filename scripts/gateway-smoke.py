"""Bounded Gateway HTTP smoke. Private synthetic credentials/owned identities arrive on stdin.

Input: {login,password,role,groupId,lessonId,date}; gateway mode needs groupId.
No response bodies, credentials, cookies or URLs are emitted.
"""
import http.cookiejar
import json
import re
import sys
import urllib.error
import urllib.parse
import urllib.request


class SmokeFailure(Exception):
    pass


def require(value):
    if not value:
        raise SmokeFailure()


class Gateway:
    def __init__(self, base):
        parsed = urllib.parse.urlsplit(base)
        require(parsed.scheme in ('http', 'https') and parsed.hostname and not parsed.username
                and not parsed.password and not parsed.query and not parsed.fragment)
        self.base = base.rstrip('/')
        self.cookies = http.cookiejar.CookieJar()
        self.client = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.cookies))
        self.token = None

    def call(self, method, path, expected, data=None, authenticated=True, json_body=True):
        headers = {'Origin': self.base, 'Content-Type': 'application/json'}
        if authenticated:
            require(isinstance(self.token, str) and bool(self.token))
            headers['Authorization'] = 'Bearer ' + self.token
        request = urllib.request.Request(self.base + path, method=method, headers=headers,
                                         data=None if data is None else json.dumps(data).encode())
        try:
            response = self.client.open(request, timeout=10)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            require(response.status == expected)
            body = response.read(2 * 1024 * 1024 + 1)
            require(len(body) <= 2 * 1024 * 1024)
        return json.loads(body) if json_body else body

    def login(self, actor):
        body = self.call('POST', '/api/auth/login', 200,
                         {'login': actor['login'], 'password': actor['password']}, False)
        self.token = body.get('accessToken')
        require(isinstance(self.token, str) and bool(self.token))
        require(any(cookie.name == 'rct_refresh' and bool(cookie.value) for cookie in self.cookies))
        session = self.call('GET', '/api/auth/session', 200)
        role = actor['role']
        require(role in ('STUDENT', 'HEADMAN', 'TEACHER', 'ADMIN'))
        if session.get('activeRole') != role:
            require(any(row.get('role') == role and row.get('selectable') is True for row in session['roles']))
            selected = self.call('PUT', '/api/auth/session/active-role', 200,
                                 {'role': role, 'expectedSessionVersion': session['sessionVersion']})
            self.token = selected['accessToken']
            require(selected['session']['activeRole'] == role and isinstance(self.token, str) and bool(self.token))


def run(mode, base, actor):
    gateway = Gateway(base)
    group_id = str(actor['groupId'])
    require(re.fullmatch(r'[1-9][0-9]*', group_id))
    if mode == 'schedule':
        health = gateway.call('GET', '/actuator/health', 200, authenticated=False)
        require(health.get('status') == 'UP')
    gateway.login(actor)
    if mode == 'gateway':
        public_key = gateway.call('GET', '/api/auth/public-key', 200, authenticated=False)
        pem = public_key.get('publicKey')
        require(isinstance(pem, str) and re.fullmatch(r'\s*-----BEGIN PUBLIC KEY-----\s+[A-Za-z0-9+/=\r\n]+-----END PUBLIC KEY-----\s*', pem))
        path = '/api/academic/groups/' + group_id
        owned = gateway.call('GET', path, 200)
        require(str(owned.get('id')) == group_id and owned.get('name') == actor['groupName'])
        gateway.call('GET', path, 401, authenticated=False, json_body=False)
        forbidden = Gateway(base)
        forbidden.login(actor['forbiddenActor'])
        forbidden.call('GET', path, 403, json_body=False)
    else:
        date = actor['date']
        require(re.fullmatch(r'\d{4}-\d{2}-\d{2}', date))
        lesson_id = str(actor['lessonId'])
        require(re.fullmatch(r'[1-9][0-9]*', lesson_id))
        path = '/api/schedule/groups/' + group_id + '/lessons?' + urllib.parse.urlencode({'dateFrom': date, 'dateTo': date})
        body = gateway.call('GET', path, 200)
        embedded = body.get('_embedded', {})
        rows = [row for value in embedded.values() if isinstance(value, list) for row in value]
        require(any(str(row.get('id')) == lesson_id and str(row.get('groupId')) == group_id
                    and row.get('date') == date for row in rows))
        gateway.call('POST', '/api/auth/logout', 204, json_body=False)
        gateway.call('GET', path, 401, json_body=False)


if __name__ == '__main__':
    stage = 'INPUT'
    try:
        require(len(sys.argv) == 3 and sys.argv[1] in ('gateway', 'schedule'))
        raw = sys.stdin.buffer.read(16385)
        require(len(raw) <= 16384)
        actor = json.loads(raw)
        stage = 'HTTP_CONTRACT'
        run(sys.argv[1], sys.argv[2], actor)
    except Exception:
        print(json.dumps({'state': 'FAIL', 'stage': stage}))
        sys.exit(1)
    print(json.dumps({'state': 'PASS', 'boundary': sys.argv[1]}))
