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
