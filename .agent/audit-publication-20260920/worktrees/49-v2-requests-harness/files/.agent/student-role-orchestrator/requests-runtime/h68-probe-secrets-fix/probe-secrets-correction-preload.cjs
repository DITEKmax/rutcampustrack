'use strict';

const { EventEmitter } = require('node:events');
const https = require('node:https');

const secrets = {
  accessToken: 'opaque-accessToken-SENTINEL',
  password: 'opaque-password-SENTINEL',
  cookie: 'opaque-cookie-SENTINEL',
  genericPassword: 'opaque-generic-password-SENTINEL'
};
// The unknown field/message deliberately carries a registered token value.
// This tests replacement outside known sensitive key names without inventing
// a secret that production could not know.
const unknownFieldValue = secrets.accessToken;
const escapedDiagnostic = 'quote " backslash \\ control\n marker\u0001';
const bearerDiagnostic = 'Bearer abc.def';
const jwtDiagnostic = 'eyJheader.payload.signature';

class SyntheticAgent {
  constructor(options) { this.options = options; }
}

function responseFor(path) {
  if (path === '/api/auth/login') {
    return {
      statusCode: 200,
      headers: { 'set-cookie': [`SID=${secrets.cookie}; Path=/`], 'cache-control': 'no-store' },
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
      'cache-control': `no-store; opaque-access=${secrets.accessToken}; opaque-password=${secrets.password}; opaque-cookie=${secrets.cookie}; opaque-unknownJSONkey=${unknownFieldValue}; password=${secrets.genericPassword}; ${bearerDiagnostic}; ${jwtDiagnostic}; ${escapedDiagnostic}`
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
    if (process.env.H68_SECRET_FIX_MODE === 'catch' && options.path === '/api/auth/session') {
      request.emit('error', new Error(
        `synthetic opaque access=${secrets.accessToken} password=${secrets.password} cookie=${secrets.cookie} opaque-unknownJSONkey=${unknownFieldValue} password=${secrets.genericPassword} ${bearerDiagnostic} ${jwtDiagnostic} ${escapedDiagnostic}`
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
