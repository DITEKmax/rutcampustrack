'use strict';

const { EventEmitter } = require('node:events');
const https = require('node:https');

const secrets = {
  accessToken: 'abcXYZ-ACCESS-SENTINEL',
  cookie: 'abcCOOKIE-SENTINEL',
  cookieSuffix: 'cookie-abc-SUFFIX-SENTINEL',
  escapedDoubleSuffix: 'escaped-double-suffix-SENTINEL',
  escapedSingleSuffix: 'escaped-single-suffix-SENTINEL'
};
const cookieHeader = `SID=${secrets.cookie}; SUFFIX=${secrets.cookieSuffix}; TOKEN=${secrets.accessToken}`;
const escapedDouble = 'password="quoted-prefix\\"' + secrets.escapedDoubleSuffix + '"';
const escapedSingle = "cookie='quoted-prefix\\'" + secrets.escapedSingleSuffix + "'";

class SyntheticAgent {
  constructor(options) { this.options = options; }
}

function responseFor(path) {
  if (path === '/api/auth/login') {
    return {
      statusCode: 200,
      headers: { 'set-cookie': [`SID=${secrets.cookie}; Path=/`, `SUFFIX=${secrets.cookieSuffix}; Path=/`, `TOKEN=${secrets.accessToken}; Path=/`], 'cache-control': 'no-store' },
      body: JSON.stringify({ accessToken: secrets.accessToken })
    };
  }
  if (path === '/api/auth/session') {
    return {
      statusCode: 200,
      headers: { 'cache-control': 'no-store' },
      body: JSON.stringify({ userId: '42', activeRole: 'STUDENT' })
    };
  }
  return {
    statusCode: 413,
    headers: {
      'cache-control': `no-store; opaque-token=${secrets.accessToken}; opaque-session=${cookieHeader}; opaque-suffix=${secrets.cookieSuffix}; ${escapedDouble}; ${escapedSingle}`
    },
    body: 'request too large'
  };
}

https.Agent = SyntheticAgent;
https.request = (options, onResponse) => {
  const request = new EventEmitter();
  request.setTimeout = () => request;
  request.write = () => true;
  request.destroy = (error) => queueMicrotask(() => request.emit('error', error));
  request.end = () => queueMicrotask(() => {
    if (process.env.H68_PROCESS_SECRET_MODE === 'catch' && options.path === '/api/auth/session') {
      request.emit('error', new Error(
        `synthetic ${secrets.accessToken} ${cookieHeader} ${secrets.cookieSuffix} ${escapedDouble} ${escapedSingle}`
      ));
      return;
    }
    const response = responseFor(options.path);
    const incoming = new EventEmitter();
    incoming.statusCode = response.statusCode;
    incoming.headers = response.headers;
    onResponse(incoming);
    incoming.emit('data', Buffer.from(response.body, 'utf8'));
    incoming.emit('end');
  });
  return request;
};
