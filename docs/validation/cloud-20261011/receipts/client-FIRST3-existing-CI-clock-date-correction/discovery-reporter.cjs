// Public reporter interface, ONLY for --list. The protected real fixture rejects this in a live run.
const fs = require('node:fs');
const path = require('node:path');
const expectedFile = '/workspace/rct-worktrees/client-ci-first3/tests/e2e/specs/mobile-real-server.spec.ts';
const expectedLines = [14, 61, 142];
module.exports = class DiscoveryReporter {
  printsToStdio() { return true; }
  onBegin(_config, suite) {
    const tests = suite.allTests();
    const valid = tests.filter((test) => test.location.file === expectedFile &&
      expectedLines.includes(test.location.line) && test.parent.project()?.name === 'chromium');
    const rows = valid.map((test) => ({ source: 'MOBILE_REAL_SERVER', line: test.location.line, project: 'chromium' }))
      .sort((left, right) => left.line - right.line);
    const pass = tests.length === 3 && rows.length === 3 && rows.every((row, index) => row.line === expectedLines[index]);
    this.discoveryPass = pass;
    const output = process.env.RCT_FOCUS3_LIST_OUTPUT;
    if (output !== path.join(process.env.RCT_FOCUS3_RUN ?? '', 'supervision', 'list.safe.json')) {
      throw new Error('Source discovery requires its private safe output');
    }
    fs.writeFileSync(output, JSON.stringify({ phase: 'SOURCE_DISCOVERY_ONLY', case_count: tests.length,
      unexpected_case_count: tests.length - valid.length, rows, pass }, null, 2) + '\n', { mode: 0o600 });
  }
  onError() { this.runnerError = true; }
  onEnd() { return { status: this.runnerError || !this.discoveryPass ? 'failed' : 'passed' }; }
  onStdOut() {}
  onStdErr() {}
};
