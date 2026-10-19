import { readFileSync } from 'node:fs'
import { parse } from '@vue/compiler-sfc'
import ts from 'typescript'
import { describe, expect, it } from 'vitest'
import { ProfileRequestError } from '../../mobile-core/src/features/profile/profile-types'
import { AuthProtocolError, AuthRequestError } from './auth-client'
import { isOfflineBootstrapRecoveryError } from './bootstrap-policy'

function hasGuardedBootstrapRecovery(source: string): boolean {
  const sfc = parse(source)
  if (sfc.errors.length || !sfc.descriptor.scriptSetup) return false
  const script = ts.createSourceFile(
    'App.ts',
    sfc.descriptor.scriptSetup.content,
    ts.ScriptTarget.Latest,
    true,
    ts.ScriptKind.TS,
  )
  const bootstraps = script.statements.filter(
    (node): node is ts.FunctionDeclaration =>
      ts.isFunctionDeclaration(node) && node.name?.text === 'bootstrap',
  )
  const body = bootstraps[0]?.body
  if (bootstraps.length !== 1 || !body) return false
  const catches = body.statements
    .filter(ts.isTryStatement)
    .flatMap((node) => (node.catchClause ? [node.catchClause] : []))
  if (catches.length !== 1) return false
  const handler = catches[0]!
  const error = handler.variableDeclaration?.name
  if (!error || !ts.isIdentifier(error)) return false
  const unwrap = (node: ts.Expression): ts.Expression =>
    ts.isParenthesizedExpression(node) ? unwrap(node.expression) : node
  const property = (node: ts.Expression, owner: string) => {
    const value = unwrap(node)
    return (
      ts.isPropertyAccessExpression(value) &&
      ts.isIdentifier(value.expression) &&
      value.expression.text === owner &&
      value.name.text === 'value'
    )
  }
  const guard = (node: ts.Expression): boolean => {
    const operands: ts.Expression[] = []
    const flatten = (expression: ts.Expression) => {
      const value = unwrap(expression)
      if (
        ts.isBinaryExpression(value) &&
        value.operatorToken.kind === ts.SyntaxKind.AmpersandAmpersandToken
      ) {
        flatten(value.left)
        flatten(value.right)
      } else operands.push(value)
    }
    flatten(node)
    if (operands.length !== 3) return false
    const [visible, role, policy] = operands
    return (
      !!visible &&
      ts.isPrefixUnaryExpression(visible) &&
      visible.operator === ts.SyntaxKind.ExclamationToken &&
      property(visible.operand, 'featureVisible') &&
      !!role &&
      ts.isBinaryExpression(role) &&
      role.operatorToken.kind === ts.SyntaxKind.ExclamationEqualsEqualsToken &&
      property(role.left, 'authView') &&
      ts.isStringLiteral(role.right) &&
      role.right.text === 'headman' &&
      !!policy &&
      ts.isCallExpression(policy) &&
      ts.isIdentifier(policy.expression) &&
      policy.expression.text === 'isOfflineBootstrapRecoveryError' &&
      policy.arguments.length === 1 &&
      ts.isIdentifier(policy.arguments[0]!) &&
      policy.arguments[0]!.text === error.text
    )
  }
  const calls: ts.CallExpression[] = []
  const guarded: ts.IfStatement[] = []
  const visit = (node: ts.Node) => {
    if (
      ts.isCallExpression(node) &&
      ts.isIdentifier(node.expression) &&
      node.expression.text === 'loadOfflineSnapshot'
    )
      calls.push(node)
    ts.forEachChild(node, visit)
  }
  visit(body)
  const visitCatch = (node: ts.Node) => {
    if (ts.isIfStatement(node) && guard(node.expression)) guarded.push(node)
    ts.forEachChild(node, visitCatch)
  }
  visitCatch(handler.block)
  if (calls.length !== 1 || guarded.length !== 1) return false
  const call = calls[0]!
  const branch = guarded[0]!.thenStatement
  if (!ts.isBlock(branch) || call.arguments.length !== 0) return false
  return branch.statements.some(
    (node) =>
      ts.isVariableStatement(node) &&
      node.declarationList.declarations.some(
        (declaration) =>
          declaration.initializer &&
          ts.isAwaitExpression(declaration.initializer) &&
          declaration.initializer.expression === call,
      ),
  )
}

describe('PWA cold bootstrap recovery policy', () => {
  it('allows a rejected fetch-shaped Auth NETWORK failure to consult the committed snapshot', () => {
    const error = new AuthRequestError(
      'refresh',
      undefined,
      { code: 'NETWORK', title: 'Нет соединения с сервером' },
      new TypeError('fetch failed'),
    )

    expect(isOfflineBootstrapRecoveryError(error)).toBe(true)
  })

  it.each([
    'INVALID_SESSION',
    'SESSION_REVOKED',
    'ACCOUNT_INVALIDATED',
    'REFRESH_REJECTED',
  ] as const)('keeps %s fail closed', (code) => {
    const error = new AuthRequestError('refresh', undefined, { code })

    expect(isOfflineBootstrapRecoveryError(error)).toBe(false)
  })

  it.each([401, 403, 409, 503] as const)(
    'keeps HTTP %s with a NETWORK problem code fail closed',
    (status) => {
      expect(
        isOfflineBootstrapRecoveryError(
          new AuthRequestError('session', status, { code: 'NETWORK' }),
        ),
      ).toBe(false)
    },
  )

  it('keeps stale-session and protocol errors fail closed', () => {
    expect(
      isOfflineBootstrapRecoveryError(new ProfileRequestError('SESSION_STATE_STALE', 'stale')),
    ).toBe(false)
    expect(
      isOfflineBootstrapRecoveryError(new AuthProtocolError('refresh', 502, 'malformed response')),
    ).toBe(false)
  })

  it('does not treat an untyped profile NETWORK error as auth transport loss', () => {
    expect(isOfflineBootstrapRecoveryError(new ProfileRequestError('NETWORK', 'network'))).toBe(
      false,
    )
  })

  it('keeps StudentApi-like and unknown bootstrap failures outside recovery', () => {
    const studentApiLike = Object.assign(new Error('BFF HTTP 403'), {
      status: 403,
      code: 'UNKNOWN',
    })

    expect(isOfflineBootstrapRecoveryError(studentApiLike)).toBe(false)
    expect(isOfflineBootstrapRecoveryError(new Error('malformed bootstrap response'))).toBe(false)
  })

  it('keeps the sole bootstrap snapshot recovery call behind the narrow guard', () => {
    const source = readFileSync(new URL('./App.vue', import.meta.url), 'utf8')
    expect(hasGuardedBootstrapRecovery(source)).toBe(true)
  })

  it('accepts wrapped syntax while rejecting recovery that escapes or weakens the guard', () => {
    const guard = `!featureVisible.value &&
      authView.value !== 'headman' && isOfflineBootstrapRecoveryError(error)`
    const recovery = 'const recovered = await loadOfflineSnapshot();'
    const source = (body: string) => `<script setup lang="ts">
      async function bootstrap() { try {} catch (error) { ${body} } }
    </script>`
    expect(hasGuardedBootstrapRecovery(source(`if (${guard}) { ${recovery} }`))).toBe(true)
    for (const body of [
      recovery,
      `if (${guard}) {} ${recovery}`,
      `if (${guard}) {} else { ${recovery} }`,
      `if (${guard}) { ${recovery} ${recovery} }`,
      `if (${guard.replace('&&', '||')}) { ${recovery} }`,
      `if (${guard.replace("!== 'headman'", "=== 'headman'")}) { ${recovery} }`,
      `if (${guard.replace('RecoveryError(error)', 'RecoveryError(other)')}) { ${recovery} }`,
      `/* ${guard} */ if (true) { ${recovery} }`,
    ])
      expect(hasGuardedBootstrapRecovery(source(body))).toBe(false)
  })
})

function deferredBootstrap() {
  let resolve!: () => void
  let reject!: (error: Error) => void
  const promise = new Promise<void>((done, failed) => {
    resolve = done
    reject = failed
  })
  return { promise, resolve, reject }
}

function reconnectControl(
  attempts: ReturnType<typeof deferredBootstrap>[],
  retainOnlineDemand = true,
) {
  const source = readFileSync(new URL('./App.vue', import.meta.url), 'utf8')
  const sfc = parse(source)
  expect(sfc.errors).toEqual([])
  const script = ts.createSourceFile(
    'App.ts',
    sfc.descriptor.scriptSetup!.content,
    ts.ScriptTarget.Latest,
    true,
  )
  const declaration = (name: string) => {
    const matches = script.statements.filter(
      (node): node is ts.FunctionDeclaration =>
        ts.isFunctionDeclaration(node) && node.name?.text === name,
    )
    expect(matches).toHaveLength(1)
    return matches[0]!
  }
  const bootstrap = declaration('bootstrap')
  const statements = bootstrap.body!.statements
  const guardedWork = statements.filter(ts.isTryStatement)
  expect(guardedWork).toHaveLength(1)
  expect(guardedWork[0]!.finallyBlock).toBeDefined()
  const prelude = statements
    .slice(0, statements.indexOf(guardedWork[0]!))
    .map((node) => node.getText(script))
    .join('\n')
  const queueDeclarations = ['queuedBootstrapAfterInvalidation', 'pendingOnlineGeneration'].map(
    (name) => {
      const matches = script.statements.filter(
        (node): node is ts.VariableStatement =>
          ts.isVariableStatement(node) &&
          node.declarationList.declarations.some(
            (item) => ts.isIdentifier(item.name) && item.name.text === name,
          ),
      )
      expect(matches).toHaveLength(1)
      return matches[0]!.getText(script)
    },
  )
  let online = declaration('goOnline').getText(script)
  if (!retainOnlineDemand) {
    // Red control: restore the old busy-bootstrap behavior without retaining its online demand.
    online = online.replace(
      /if \(bootstrapping\.value\) \{\s*pendingOnlineGeneration = auth\.currentGeneration\(\);\s*return;\s*\}/,
      '',
    )
    expect(online).not.toContain('pendingOnlineGeneration = auth.currentGeneration()')
  }
  const state = {
    generation: 1,
    automatic: true,
    authView: { value: 'student' },
    bootstrapping: { value: false },
    offline: { value: false },
    readOnly: { value: false },
    sessionReady: { value: true },
    navigator: { onLine: true },
    calls: [] as { refresh?: boolean }[],
    attempts: [...attempts],
  }
  // Execute the actual App admission guards, handlers and entire finally block.
  // Deferred work substitutes only the awaited HTTP/owner-building body, never queue logic.
  const code = ts.transpileModule(
    `const { authView, bootstrapping, offline, readOnly, sessionReady, navigator } = state;
    const auth = { currentGeneration: () => state.generation,
      canAutoBootstrap: () => state.automatic };
    const push = { invalidate: () => Promise.resolve() };
    ${queueDeclarations.join('\n')}
    ${declaration('isPasswordRecoveryView').getText(script)}
    async function bootstrap(options = {}) {
      ${prelude}
      state.calls.push(options);
      const attempt = state.attempts.shift();
      if (!attempt) throw new Error('Unexpected extra bootstrap');
      try {
        await attempt.promise;
        offline.value = false;
        readOnly.value = false;
      } catch {
        offline.value = true;
        readOnly.value = true;
      } finally ${guardedWork[0]!.finallyBlock!.getText(script)}
    }
    ${online}
    ${declaration('goOffline').getText(script)}
    return { bootstrap, goOnline, goOffline,
      invalidate: () => { queuedBootstrapAfterInvalidation = true; } };`,
    { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.None } },
  ).outputText
  const control = new Function('state', code)(state) as {
    bootstrap: (options?: { refresh?: boolean }) => Promise<void>
    goOnline: () => void
    goOffline: () => void
    invalidate: () => void
  }
  return { ...control, state }
}

describe('PWA online demand during an older bootstrap', () => {
  it('reproduces the lost-demand counterexample with the old busy guard', async () => {
    const first = deferredBootstrap()
    const control = reconnectControl([first], false)
    const running = control.bootstrap()
    control.goOffline()
    control.goOnline()
    first.reject(new Error('Earlier bootstrap failed'))
    await running
    expect(control.state.calls).toEqual([{}])
    expect(control.state.readOnly.value).toBe(true)
  })

  it.each(['resolve', 'reject'] as const)(
    'rechecks once after the older bootstrap %s, retaining read-only authority until recheck',
    async (settle) => {
      const first = deferredBootstrap()
      const replay = deferredBootstrap()
      const control = reconnectControl([first, replay])
      const running = control.bootstrap()
      control.goOffline()
      control.goOnline()
      expect(control.state.calls).toEqual([{}])
      if (settle === 'resolve') first.resolve()
      else first.reject(new Error('Earlier bootstrap failed'))
      await running
      expect(control.state.calls).toEqual([{}, {}])
      expect(control.state.offline.value).toBe(true)
      expect(control.state.readOnly.value).toBe(true)
      replay.resolve()
      await Promise.resolve()
      expect(control.state.calls).toHaveLength(2)
      expect(control.state.bootstrapping.value).toBe(false)
    },
  )

  it('coalesces overlapping online events and never retries a failed replay by itself', async () => {
    const first = deferredBootstrap()
    const replay = deferredBootstrap()
    const control = reconnectControl([first, replay])
    const running = control.bootstrap()
    control.goOnline()
    control.goOnline()
    control.goOnline()
    first.resolve()
    await running
    expect(control.state.calls).toEqual([{}, {}])
    replay.reject(new Error('Replay failed'))
    await Promise.resolve()
    expect(control.state.calls).toHaveLength(2)
    expect(control.state.bootstrapping.value).toBe(false)
    expect(control.state.readOnly.value).toBe(true)
  })

  it.each([
    'offline',
    'logout',
    'new-generation',
    'unmount',
    'auto-bootstrap-disabled',
    'password-recovery',
    'browser-offline',
  ] as const)('cancels pending online demand after %s', async (reason) => {
    const first = deferredBootstrap()
    const control = reconnectControl([first])
    const running = control.bootstrap()
    control.goOnline()
    if (reason === 'offline') control.goOffline()
    else if (reason === 'logout') {
      control.state.automatic = false
      control.state.generation += 1
    } else if (reason === 'new-generation' || reason === 'unmount') control.state.generation += 1
    else if (reason === 'auto-bootstrap-disabled') control.state.automatic = false
    else if (reason === 'password-recovery') control.state.authView.value = 'password-recovery'
    else control.state.navigator.onLine = false
    first.reject(new Error('Earlier bootstrap failed'))
    await running
    expect(control.state.calls).toEqual([{}])
  })

  it.each(['absent', 'stale', 'valid'] as const)(
    'preserves authority reconciliation with %s online demand',
    async (demand) => {
      const first = deferredBootstrap()
      const replay = deferredBootstrap()
      const control = reconnectControl([first, replay])
      const running = control.bootstrap()
      if (demand !== 'absent') control.goOnline()
      if (demand === 'stale') control.state.generation += 1
      control.invalidate()
      first.reject(new Error('Earlier authority operation settled'))
      await running
      expect(control.state.calls).toEqual([{}, demand === 'valid' ? {} : { refresh: false }])
      replay.resolve()
      await Promise.resolve()
      expect(control.state.calls).toHaveLength(2)
    },
  )

  it('does not create a reconnect request just because an ordinary bootstrap failed', async () => {
    const first = deferredBootstrap()
    const control = reconnectControl([first])
    const running = control.bootstrap()
    first.reject(new Error('Earlier bootstrap failed'))
    await running
    expect(control.state.calls).toEqual([{}])
  })
})
