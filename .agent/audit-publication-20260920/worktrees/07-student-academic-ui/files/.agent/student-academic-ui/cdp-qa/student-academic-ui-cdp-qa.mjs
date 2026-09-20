#!/usr/bin/env node

/*
 * FE13 standalone CDP QA runner.
 *
 * This file intentionally uses only Node built-ins and the Node 24 global
 * fetch/WebSocket implementations. It owns one freshly created Chrome target,
 * drives the already-running local harness, writes measured evidence, and
 * closes both the WebSocket and target in finally.
 */

import fs from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const SCRIPT_PATH = fileURLToPath(import.meta.url)
const DEFAULT_OUT = path.dirname(SCRIPT_PATH)
const DEFAULT_CDP = 'http://127.0.0.1:9223'
const DEFAULT_BASE = 'http://127.0.0.1:18210'
const JSON_NAME = 'student-academic-ui-cdp-qa.json'
const COMMAND_TIMEOUT_MS = 12_000
const PAGE_TIMEOUT_MS = 10_000
const POLL_MS = 60
const QUIET_MS = 120
const GEOMETRY_EPSILON = 1.5

const BASE_FIXTURES = [
  '4593-142',
  '4593-848365',
  '4593-848496',
  '4595-293',
  '4595-848430',
  '4596-365',
  '4710-232',
  '4768-228',
  '4603-142',
  '4603-848696',
  '4798-142',
  '4798-200',
  '4798-285',
]

const RESPONSIVE_FIXTURES = ['4593-142', '4603-142', '4603-848696']
const RESPONSIVE_VARIANTS = [
  { width: 390, height: 844, rootFont: 16, theme: 'light' },
  { width: 320, height: 844, rootFont: 16, theme: 'dark' },
  { width: 320, height: 844, rootFont: 24, theme: 'dark' },
  { width: 390, height: 844, rootFont: 24, theme: 'light' },
]

const USED_CDP_DOMAINS = [
  'Page',
  'Runtime',
  'Emulation',
  'Accessibility',
  'Input',
]

function sleep(milliseconds) {
  return new Promise((resolve) => setTimeout(resolve, milliseconds))
}

function errorText(error) {
  if (error instanceof Error) return error.message
  return String(error)
}

function errorRecord(error) {
  return {
    name: error instanceof Error && error.name ? error.name : 'Error',
    message: errorText(error),
  }
}

function parseArgs(argv) {
  const options = {
    cdp: DEFAULT_CDP,
    base: DEFAULT_BASE,
    out: DEFAULT_OUT,
    help: false,
  }
  const keys = new Set(['cdp', 'base', 'out'])

  for (let index = 0; index < argv.length; index += 1) {
    const argument = argv[index]
    if (argument === '--help' || argument === '-h') {
      options.help = true
      continue
    }
    if (!argument.startsWith('--') || !keys.has(argument.slice(2))) {
      throw new Error('Unknown argument: ' + argument)
    }
    const key = argument.slice(2)
    const value = argv[index + 1]
    if (!value || value.startsWith('--')) {
      throw new Error('Missing value for --' + key)
    }
    options[key] = value
    index += 1
  }

  return options
}

function usage() {
  return [
    'Usage: node student-academic-ui-cdp-qa.mjs [options]',
    '',
    'Options:',
    '  --cdp <http://127.0.0.1:9223>  Chrome DevTools HTTP endpoint',
    '  --base <http://127.0.0.1:18210> Vite harness base URL',
    '  --out <directory>               JSON and PNG output directory',
  ].join('\n')
}

function localOrigin(value, label) {
  const candidate = value.includes('://') ? value : 'http://' + value
  let parsed
  try {
    parsed = new URL(candidate)
  } catch (error) {
    throw new Error(label + ' must be a valid local HTTP URL: ' + errorText(error))
  }
  if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
    throw new Error(label + ' must use http or https')
  }
  if (parsed.hostname !== '127.0.0.1' && parsed.hostname !== 'localhost') {
    throw new Error(label + ' must target localhost or 127.0.0.1')
  }
  if (parsed.username || parsed.password || parsed.hash) {
    throw new Error(label + ' must not contain credentials or a fragment')
  }
  return parsed.origin
}

function harnessUrl(baseOrigin, fixture, variant, terminal = true) {
  const url = new URL(baseOrigin + '/')
  url.searchParams.set('fixture', fixture)
  url.searchParams.set('theme', variant.theme)
  url.searchParams.set('rootFont', String(variant.rootFont))
  url.searchParams.set('terminal', String(terminal))
  return url.toString()
}

async function responseJson(response, requestUrl) {
  const body = await response.text()
  if (!response.ok) {
    throw new Error(
      'HTTP ' + response.status + ' for ' + requestUrl + ': ' + body.slice(0, 400),
    )
  }
  try {
    return JSON.parse(body)
  } catch (error) {
    throw new Error(
      'Invalid JSON from ' + requestUrl + ': ' + errorText(error),
    )
  }
}

async function fetchJson(requestUrl, init = undefined) {
  return responseJson(await fetch(requestUrl, init), requestUrl)
}

async function createTarget(cdpOrigin, pageUrl) {
  const requestUrl = cdpOrigin + '/json/new?' + encodeURIComponent(pageUrl)
  let response = await fetch(requestUrl, { method: 'PUT' })
  if (response.status === 405 || response.status === 404) {
    response = await fetch(requestUrl, { method: 'GET' })
  }
  const target = await responseJson(response, requestUrl)
  if (!target || typeof target.id !== 'string' || !target.webSocketDebuggerUrl) {
    throw new Error('Chrome returned a target without id or webSocketDebuggerUrl')
  }
  return target
}

async function closeTarget(cdpOrigin, targetId) {
  const requestUrl = cdpOrigin + '/json/close/' + encodeURIComponent(targetId)
  const response = await fetch(requestUrl)
  const body = await response.text()
  return {
    status: response.status,
    ok: response.ok,
    body: body.slice(0, 200),
  }
}

class CdpClient {
  constructor(socket) {
    this.socket = socket
    this.nextId = 1
    this.pending = new Map()
    this.listeners = new Map()
    this.capture = null
    this.closedError = null

    socket.addEventListener('message', (event) => {
      this.handleMessage(event.data)
    })
    socket.addEventListener('close', () => {
      const closeError = this.closedError ?? new Error('CDP WebSocket closed')
      for (const pending of this.pending.values()) {
        clearTimeout(pending.timer)
        pending.reject(closeError)
      }
      this.pending.clear()
    })
    socket.addEventListener('error', () => {
      if (!this.closedError) this.closedError = new Error('CDP WebSocket error')
    })
  }

  static async connect(webSocketUrl) {
    if (typeof WebSocket !== 'function') {
      throw new Error('Node global WebSocket is unavailable; Node 24 is required')
    }

    return new Promise((resolve, reject) => {
      let socket
      try {
        socket = new WebSocket(webSocketUrl)
      } catch (error) {
        reject(error)
        return
      }

      const timeout = setTimeout(() => {
        try {
          socket.close()
        } catch {
          // The connection is already unusable.
        }
        reject(new Error('Timed out opening CDP WebSocket'))
      }, COMMAND_TIMEOUT_MS)

      socket.addEventListener('open', () => {
        clearTimeout(timeout)
        resolve(new CdpClient(socket))
      }, { once: true })
      socket.addEventListener('error', () => {
        clearTimeout(timeout)
        reject(new Error('Unable to open CDP WebSocket'))
      }, { once: true })
    })
  }

  on(method, listener) {
    const listeners = this.listeners.get(method) ?? []
    listeners.push(listener)
    this.listeners.set(method, listeners)
  }

  dispatch(method, params) {
    for (const listener of this.listeners.get(method) ?? []) {
      try {
        listener(params)
      } catch {
        // Evidence listeners must not interrupt the CDP transport.
      }
    }
  }

  handleMessage(rawData) {
    let text
    if (typeof rawData === 'string') {
      text = rawData
    } else if (rawData instanceof ArrayBuffer) {
      text = Buffer.from(rawData).toString('utf8')
    } else if (ArrayBuffer.isView(rawData)) {
      text = Buffer.from(rawData.buffer, rawData.byteOffset, rawData.byteLength).toString('utf8')
    } else {
      return
    }

    let message
    try {
      message = JSON.parse(text)
    } catch {
      return
    }

    if (typeof message.id === 'number') {
      const pending = this.pending.get(message.id)
      if (!pending) return
      this.pending.delete(message.id)
      clearTimeout(pending.timer)
      if (message.error) {
        pending.reject(new Error(
          'CDP ' + pending.method + ': ' + message.error.message,
        ))
        return
      }
      pending.resolve(message.result ?? {})
      return
    }

    if (typeof message.method === 'string') {
      this.dispatch(message.method, message.params ?? {})
    }
  }

  send(method, params = {}) {
    if (this.socket.readyState !== WebSocket.OPEN) {
      return Promise.reject(this.closedError ?? new Error('CDP WebSocket is not open'))
    }
    const id = this.nextId
    this.nextId += 1
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pending.delete(id)
        reject(new Error('Timed out waiting for CDP ' + method))
      }, COMMAND_TIMEOUT_MS)
      this.pending.set(id, { method, resolve, reject, timer })
      try {
        this.socket.send(JSON.stringify({ id, method, params }))
      } catch (error) {
        clearTimeout(timer)
        this.pending.delete(id)
        reject(error)
      }
    })
  }

  async evaluate(pageFunction, argument = null) {
    const expression = '(' + pageFunction.toString() + ')(' + JSON.stringify(argument) + ')'
    const result = await this.send('Runtime.evaluate', {
      expression,
      awaitPromise: true,
      returnByValue: true,
      userGesture: true,
    })
    if (result.exceptionDetails) {
      const description = result.exceptionDetails.exception?.description
        ?? result.exceptionDetails.text
        ?? 'page evaluation failed'
      throw new Error('Runtime.evaluate: ' + description)
    }
    return result.result?.value
  }

  beginCapture(label) {
    this.capture = {
      label,
      errors: [],
      warnings: [],
    }
  }

  record(level, source, event, text) {
    if (!this.capture) return
    const record = {
      source,
      level,
      text: String(text || '(no message)'),
      url: event?.url ?? null,
      timestamp: typeof event?.timestamp === 'number'
        ? event.timestamp
        : null,
    }
    if (level === 'error') {
      this.capture.errors.push(record)
    } else {
      this.capture.warnings.push(record)
    }
  }

  endCapture() {
    const capture = this.capture ?? { label: null, errors: [], warnings: [] }
    this.capture = null
    return capture
  }

  async close() {
    if (!this.socket || this.socket.readyState === WebSocket.CLOSED) return
    try {
      this.socket.close()
    } catch (error) {
      this.closedError = this.closedError ?? error
    }
    await new Promise((resolve) => {
      if (this.socket.readyState === WebSocket.CLOSED) {
        resolve()
        return
      }
      const timer = setTimeout(resolve, 1_000)
      this.socket.addEventListener('close', () => {
        clearTimeout(timer)
        resolve()
      }, { once: true })
    })
  }
}

function attachProtocolCapture(client) {
  client.on('Runtime.consoleAPICalled', (event) => {
    const values = (event.args ?? []).map((argument) => (
      argument.value
      ?? argument.unserializableValue
      ?? argument.description
      ?? argument.type
      ?? ''
    ))
    client.record(
      event.type === 'error' ? 'error' : 'warning',
      'Runtime.consoleAPICalled',
      event,
      values.join(' '),
    )
  })
  client.on('Runtime.exceptionThrown', (event) => {
    client.record(
      'error',
      'Runtime.exceptionThrown',
      event,
      event.exceptionDetails?.exception?.description
        ?? event.exceptionDetails?.text
        ?? 'Uncaught page exception',
    )
  })
  client.on('Log.entryAdded', (event) => {
    client.record(
      event.entry?.level === 'error' ? 'error' : 'warning',
      'Log.entryAdded',
      event.entry ?? event,
      event.entry?.text ?? 'Browser log entry',
    )
  })
}

function uniqueConsoleRecords(records) {
  const seen = new Set()
  const unique = []
  for (const record of records) {
    const key = record.source + '|' + record.level + '|' + record.text
    if (seen.has(key)) continue
    seen.add(key)
    unique.push(record)
  }
  return unique
}

function mergeConsole(capture, pageErrors) {
  const favicon404 = (record) => {
    if (
      record.source !== 'Log.entryAdded'
      || record.text !== 'Failed to load resource: the server responded with a status of 404 (Not Found)'
      || typeof record.url !== 'string'
    ) return false
    try {
      const url = new URL(record.url)
      return (
        (url.protocol === 'http:' || url.protocol === 'https:')
        && (url.hostname === 'localhost' || url.hostname === '127.0.0.1')
        && url.pathname === '/favicon.ico'
      )
    } catch {
      return false
    }
  }
  const capturedErrors = capture?.errors ?? []
  const demotedWarnings = capturedErrors
    .filter(favicon404)
    .map((record) => ({ ...record, level: 'warning' }))
  const errors = uniqueConsoleRecords([
    ...capturedErrors.filter((record) => !favicon404(record)),
    ...((pageErrors ?? []).map((entry) => ({
      source: 'window',
      level: 'error',
      text: entry.text ?? 'window error',
      timestamp: null,
    }))),
  ])
  const warnings = uniqueConsoleRecords([
    ...(capture?.warnings ?? []),
    ...demotedWarnings,
  ])
  return {
    errors,
    warnings,
  }
}

const PAGE_READY_FUNCTION = function (expectedFixture) {
  const root = document.querySelector('.harness-root')
  const screenRoots = document.querySelectorAll(
    '.attendance-screen, .statistics-screen, .statistics-detail',
  )
  return {
    ready: document.readyState === 'complete',
    root: Boolean(root),
    fixture: root?.dataset.fixture ?? null,
    screenRootCount: screenRoots.length,
    expected: expectedFixture,
  }
}

const PAGE_INSTALL_ERRORS_FUNCTION = function () {
  window.__rctCdpQaErrors = []
  window.addEventListener('error', (event) => {
    window.__rctCdpQaErrors.push({
      text: event.error?.stack ?? event.message ?? 'window error',
    })
  })
  window.addEventListener('unhandledrejection', (event) => {
    window.__rctCdpQaErrors.push({
      text: event.reason?.stack ?? String(event.reason ?? 'unhandled rejection'),
    })
  })
  return true
}

const PAGE_GET_ERRORS_FUNCTION = function () {
  return Array.isArray(window.__rctCdpQaErrors)
    ? window.__rctCdpQaErrors
    : []
}

const PAGE_SNAPSHOT_FUNCTION = function (input) {
  const geometryEpsilon = 1.5
  const rectFor = (element) => {
    if (!element) return null
    const rect = element.getBoundingClientRect()
    return {
      x: rect.x,
      y: rect.y,
      width: rect.width,
      height: rect.height,
      left: rect.left,
      right: rect.right,
      top: rect.top,
      bottom: rect.bottom,
    }
  }
  const styleFor = (element) => {
    if (!element) return null
    const style = getComputedStyle(element)
    return {
      display: style.display,
      position: style.position,
      width: style.width,
      height: style.height,
      overflow: style.overflow,
      clip: style.clip,
      clipPath: style.clipPath,
      whiteSpace: style.whiteSpace,
      visibility: style.visibility,
      gridTemplateColumns: style.gridTemplateColumns,
      gap: style.gap,
    }
  }
  const textFor = (element) => (
    element?.textContent?.replace(/\s+/g, ' ').trim() ?? ''
  )
  const root = document.querySelector('.harness-root')
  const screen = document.querySelector(
    '.attendance-screen, .statistics-screen, .statistics-detail',
  )
  const documentElement = document.documentElement
  const body = document.body
  const typeCards = Array.from(document.querySelectorAll('.statistics-type-card')).map((card) => ({
    type: card.getAttribute('data-type'),
    rect: rectFor(card),
    text: textFor(card),
  }))
  const typeButtons = Array.from(
    document.querySelectorAll('.statistics-type-filter__button'),
  ).map((button) => ({
    text: textFor(button),
    selected: button.getAttribute('aria-pressed') === 'true',
    disabled: Boolean(button.disabled),
    rect: rectFor(button),
  }))
  const rangeButtons = Array.from(
    document.querySelectorAll('.statistics-chart__range-button'),
  ).map((button) => ({
    text: textFor(button),
    selected: button.getAttribute('aria-pressed') === 'true',
    disabled: Boolean(button.disabled),
    rect: rectFor(button),
  }))
  const chartPaths = Array.from(
    document.querySelectorAll('.statistics-chart__area'),
  ).map((pathElement) => ({
    className: pathElement.getAttribute('class'),
    d: pathElement.getAttribute('d') ?? '',
  }))
  const hiddenLegend = document.querySelector(
    '.statistics-chart__legend.statistics-visually-hidden',
  )
  const hiddenItems = hiddenLegend
    ? Array.from(hiddenLegend.querySelectorAll('li')).map((item) => ({
      text: textFor(item),
      rect: rectFor(item),
      css: styleFor(item),
    }))
    : []
  const hiddenChart = {
    exists: Boolean(hiddenLegend),
    rect: rectFor(hiddenLegend),
    css: styleFor(hiddenLegend),
    text: textFor(hiddenLegend),
    itemCount: hiddenItems.length,
    items: hiddenItems,
  }
  const attendanceHistories = Array.from(
    document.querySelectorAll('.attendance-type-card__history'),
  ).map((history) => ({
    rect: rectFor(history),
    css: styleFor(history),
    segmentCount: history.querySelectorAll(
      '.attendance-type-card__segment',
    ).length,
    segments: Array.from(
      history.querySelectorAll('.attendance-type-card__segment'),
    ).map((segment) => rectFor(segment)),
  }))
  const statisticsHistories = Array.from(
    document.querySelectorAll('.statistics-type-card__history'),
  ).map((history) => ({
    rect: rectFor(history),
    css: styleFor(history),
    segmentCount: history.querySelectorAll(
      '.statistics-type-card__segment',
    ).length,
    segments: Array.from(
      history.querySelectorAll('.statistics-type-card__segment'),
    ).map((segment) => rectFor(segment)),
  }))
  const activeDockItems = Array.from(
    document.querySelectorAll('[aria-current="page"]'),
  ).map((element) => ({
    text: textFor(element),
    rect: rectFor(element),
    className: element.getAttribute('class'),
  }))
  const internalSelectors = [
    '.attendance-day-rail',
    '.attendance-view-switch',
    '.statistics-type-filter__options',
    '.statistics-chart__range',
    '.statistics-chart__plot',
    '.statistics-chart__legend',
  ]
  const internalScrollables = internalSelectors.flatMap((selector) => (
    Array.from(document.querySelectorAll(selector)).map((element) => ({
      selector,
      clientWidth: element.clientWidth,
      scrollWidth: element.scrollWidth,
      clientHeight: element.clientHeight,
      scrollHeight: element.scrollHeight,
    }))
  ))
  const documentClientWidth = documentElement?.clientWidth ?? 0
  const documentScrollWidth = documentElement?.scrollWidth ?? 0
  const bodyClientWidth = body?.clientWidth ?? 0
  const bodyScrollWidth = body?.scrollWidth ?? 0
  const maxScrollWidth = Math.max(documentScrollWidth, bodyScrollWidth)
  const cardsWithinViewport = typeCards.length > 0 && typeCards.every((card) => (
    card.rect
    && card.rect.top >= -geometryEpsilon
    && card.rect.bottom <= (input.height || window.innerHeight) + geometryEpsilon
  ))
  const subjectSummaries = Array.from(
    document.querySelectorAll('.statistics-subject-summary'),
  ).map((button) => ({
    text: textFor(button),
    rect: rectFor(button),
  }))

  return {
    fixture: root?.dataset.fixture ?? null,
    screen: root?.dataset.screen ?? null,
    theme: root?.dataset.theme ?? null,
    root: {
      exists: Boolean(root),
      count: document.querySelectorAll('.harness-root').length,
      rect: rectFor(root),
      css: styleFor(root),
      screenRootCount: document.querySelectorAll(
        '.attendance-screen, .statistics-screen, .statistics-detail',
      ).length,
    },
    viewport: {
      requestedWidth: input.width,
      requestedHeight: input.height,
      innerWidth: window.innerWidth,
      innerHeight: window.innerHeight,
      devicePixelRatio: window.devicePixelRatio,
      visualViewport: window.visualViewport
        ? {
          width: window.visualViewport.width,
          height: window.visualViewport.height,
          scale: window.visualViewport.scale,
        }
        : null,
    },
    document: {
      clientWidth: documentClientWidth,
      scrollWidth: documentScrollWidth,
      clientHeight: documentElement?.clientHeight ?? 0,
      scrollHeight: documentElement?.scrollHeight ?? 0,
      bodyClientWidth,
      bodyScrollWidth,
    },
    overflow: {
      global: maxScrollWidth > documentClientWidth + geometryEpsilon,
      maxScrollWidth,
      delta: maxScrollWidth - documentClientWidth,
      internal: internalScrollables,
    },
    screenRoot: {
      className: screen?.getAttribute('class') ?? null,
      rect: rectFor(screen),
    },
    typeCards,
    cardsWithinViewport,
    typeButtons,
    rangeButtons,
    chartPaths,
    aggregateText: textFor(document.querySelector('.statistics-detail__aggregate')),
    hiddenChart,
    attendanceHistories,
    statisticsHistories,
    activeDockItems,
    subjectSummaries,
    requestTriggers: Array.from(
      document.querySelectorAll('[data-request-trigger]'),
    ).map((element) => ({
      value: element.getAttribute('data-request-trigger'),
      disabled: Boolean(element.disabled),
      expanded: element.getAttribute('aria-expanded'),
      rect: rectFor(element),
    })),
    actionButtons: Array.from(
      document.querySelectorAll('.attendance-lesson-row__action'),
    ).map((element) => ({
      text: textFor(element),
      disabled: Boolean(element.disabled),
      rect: rectFor(element),
    })),
  }
}

const PAGE_STATS_STATE_FUNCTION = function () {
  const textFor = (element) => (
    element?.textContent?.replace(/\s+/g, ' ').trim() ?? ''
  )
  const paths = Array.from(
    document.querySelectorAll('.statistics-chart__area'),
  ).map((element) => element.getAttribute('d') ?? '')
  const legend = textFor(document.querySelector(
    '.statistics-chart__legend.statistics-visually-hidden',
  ))
  return {
    selected: Array.from(
      document.querySelectorAll('.statistics-type-filter__button'),
    ).filter((button) => button.getAttribute('aria-pressed') === 'true')
      .map(textFor),
    types: Array.from(
      document.querySelectorAll('.statistics-type-filter__button'),
    ).map((button) => ({
      text: textFor(button),
      selected: button.getAttribute('aria-pressed') === 'true',
      disabled: Boolean(button.disabled),
    })),
    cards: Array.from(
      document.querySelectorAll('.statistics-type-card'),
    ).map((card) => card.getAttribute('data-type') ?? ''),
    aggregateText: textFor(document.querySelector(
      '.statistics-detail__aggregate',
    )),
    pathSignature: paths.join('|'),
    legendSignature: legend,
    range: Array.from(
      document.querySelectorAll('.statistics-chart__range-button'),
    ).find((button) => button.getAttribute('aria-pressed') === 'true')
      ?.textContent?.replace(/\s+/g, ' ').trim() ?? null,
  }
}

const PAGE_STATS_CLICK_SELECTED_TYPE_FUNCTION = function () {
  const button = Array.from(
    document.querySelectorAll('.statistics-type-filter__button'),
  ).find((candidate) => (
    candidate.getAttribute('aria-pressed') === 'true'
    && !candidate.disabled
  ))
  if (!button) return { ok: false, reason: 'no selected enabled type button' }
  const text = button.textContent?.replace(/\s+/g, ' ').trim() ?? ''
  button.click()
  return { ok: true, text }
}

const PAGE_STATS_CLICK_LAST_TYPE_FUNCTION = function () {
  const buttons = Array.from(
    document.querySelectorAll('.statistics-type-filter__button'),
  )
  const button = buttons.find((candidate) => (
    candidate.getAttribute('aria-pressed') === 'true'
  ))
  if (!button) return { ok: false, reason: 'no selected type button' }
  const before = {
    text: button.textContent?.replace(/\s+/g, ' ').trim() ?? '',
    disabled: Boolean(button.disabled),
  }
  button.click()
  return { ok: true, before }
}

const PAGE_STATS_CLICK_RANGE_FUNCTION = function (label) {
  const button = Array.from(
    document.querySelectorAll('.statistics-chart__range-button'),
  ).find((candidate) => (
    candidate.textContent?.replace(/\s+/g, ' ').trim() === label
  ))
  if (!button) return { ok: false, reason: 'range button not found: ' + label }
  button.click()
  return { ok: true, label }
}

const PAGE_ATTENDANCE_REQUEST_DETAILS_FUNCTION = function () {
  const trigger = Array.from(
    document.querySelectorAll('[data-request-trigger]'),
  ).find((candidate) => !candidate.disabled)
  const enabledAction = document.querySelector(
    '.attendance-lesson-row__action:not(:disabled)',
  )
  return {
    trigger: Boolean(trigger),
    triggerValue: trigger?.getAttribute('data-request-trigger') ?? null,
    triggerExpanded: trigger?.getAttribute('aria-expanded') ?? null,
    enabledAction: Boolean(enabledAction),
    actionText: enabledAction?.textContent?.replace(/\s+/g, ' ').trim() ?? null,
    inline: Boolean(document.querySelector('.attendance-inline__header')),
  }
}

const PAGE_FOCUS_REQUEST_TRIGGER_FUNCTION = function () {
  const trigger = Array.from(
    document.querySelectorAll('[data-request-trigger]'),
  ).find((candidate) => !candidate.disabled)
  if (!trigger) return { ok: false, reason: 'enabled request trigger not found' }
  trigger.focus()
  return {
    ok: document.activeElement === trigger,
    value: trigger.getAttribute('data-request-trigger'),
    activeClass: document.activeElement?.getAttribute('class') ?? null,
  }
}

const PAGE_FOCUS_ENABLED_ACTION_FUNCTION = function () {
  const action = document.querySelector(
    '.attendance-lesson-row__action:not(:disabled)',
  )
  if (!action) return { ok: false, reason: 'enabled request action not found' }
  action.focus()
  return {
    ok: document.activeElement === action,
    text: action.textContent?.replace(/\s+/g, ' ').trim() ?? '',
    activeClass: document.activeElement?.getAttribute('class') ?? null,
  }
}

const PAGE_ACTIVE_FOCUS_FUNCTION = function () {
  const active = document.activeElement
  return {
    tagName: active?.tagName ?? null,
    className: active?.getAttribute('class') ?? null,
    ariaLabel: active?.getAttribute('aria-label') ?? null,
    requestTrigger: active?.getAttribute('data-request-trigger') ?? null,
    inline: Boolean(document.querySelector('.attendance-inline__header')),
  }
}

const PAGE_CLICK_FIRST_SUBJECT_FUNCTION = function () {
  const trigger = document.querySelector(
    '.attendance-subject-list__trigger',
  )
  if (!(trigger instanceof HTMLElement)) {
    return { ok: false, reason: 'attendance subject trigger not found' }
  }
  trigger.click()
  return { ok: true, text: trigger.textContent?.replace(/\s+/g, ' ').trim() ?? '' }
}

async function enableDomains(client) {
  await client.send('Page.enable')
  await client.send('Runtime.enable')
  await client.send('Log.enable')
  await client.send('Accessibility.enable')
}

async function setViewport(client, variant) {
  await client.send('Emulation.setDeviceMetricsOverride', {
    width: variant.width,
    height: variant.height,
    deviceScaleFactor: 1,
    mobile: false,
    scale: 1,
    screenWidth: variant.width,
    screenHeight: variant.height,
  })
}

async function waitForHarness(client, fixture, timeoutMs = PAGE_TIMEOUT_MS) {
  const deadline = Date.now() + timeoutMs
  let latest = null
  while (Date.now() < deadline) {
    try {
      latest = await client.evaluate(PAGE_READY_FUNCTION, fixture)
      if (
        latest?.ready
        && latest.root
        && latest.fixture === fixture
        && latest.screenRootCount === 1
      ) {
        return latest
      }
    } catch {
      // A navigation can briefly invalidate the current execution context.
    }
    await sleep(POLL_MS)
  }
  throw new Error(
    'Harness did not become ready for fixture ' + fixture
      + '; last state: ' + JSON.stringify(latest),
  )
}

async function waitForPageValue(client, pageFunction, argument, predicate, timeoutMs = PAGE_TIMEOUT_MS) {
  const deadline = Date.now() + timeoutMs
  let latest = null
  while (Date.now() < deadline) {
    try {
      latest = await client.evaluate(pageFunction, argument)
      if (predicate(latest)) return latest
    } catch {
      // Vue can replace the page context during a click-driven update.
    }
    await sleep(POLL_MS)
  }
  throw new Error('Timed out waiting for page condition; last state: ' + JSON.stringify(latest))
}

async function navigate(client, url, fixture) {
  const navigation = await client.send('Page.navigate', { url })
  if (navigation.errorText) {
    throw new Error('Page.navigate: ' + navigation.errorText)
  }
  const ready = await waitForHarness(client, fixture)
  await sleep(QUIET_MS)
  return ready
}

async function getPageErrors(client) {
  try {
    return await client.evaluate(PAGE_GET_ERRORS_FUNCTION)
  } catch {
    return []
  }
}

async function capturePng(client, outputPath) {
  const screenshot = await client.send('Page.captureScreenshot', {
    format: 'png',
    fromSurface: true,
    captureBeyondViewport: false,
  })
  const bytes = Buffer.from(screenshot.data, 'base64')
  await fs.writeFile(outputPath, bytes)
  return {
    path: outputPath,
    bytes: bytes.byteLength,
  }
}

async function getAccessibilitySnapshot(client, hiddenItemCount) {
  const response = await client.send('Accessibility.getFullAXTree')
  const nodes = Array.isArray(response.nodes) ? response.nodes : []
  const names = nodes
    .map((node) => ({
      nodeId: node.nodeId ?? null,
      role: node.role?.value ?? null,
      name: typeof node.name?.value === 'string' ? node.name.value.trim() : '',
      ignored: Boolean(node.ignored),
    }))
    .filter((node) => node.name)
  const russianNames = names
    .filter((node) => /[А-Яа-яЁё]/u.test(node.name))
    .slice(0, 100)
  const pointNames = russianNames.filter((node) => (
    /Период\s+/u.test(node.name)
    && /Присутствие|отсутствие|уважительная причина|Закрыто/u.test(node.name)
  ))
  return {
    nodeCount: nodes.length,
    russianNames,
    pointNames,
    hiddenItemCount,
    meaningfulRussianPointData: hiddenItemCount > 0 && pointNames.length > 0,
  }
}

function scenarioAssertions(result) {
  const snapshot = result.snapshot
  return {
    root: Boolean(
      snapshot?.root?.exists
      && snapshot.root.count === 1
      && snapshot.root.screenRootCount === 1
      && snapshot.fixture === result.fixture,
    ),
    globalOverflow: Boolean(snapshot && !snapshot.overflow.global),
    consoleErrors: Boolean(result.console && result.console.errors.length === 0),
  }
}

async function inspectScenario(client, options) {
  const result = {
    key: options.key,
    fixture: options.fixture,
    viewport: options.variant,
    terminal: options.terminal,
    url: options.url,
    status: 'FAIL',
    snapshot: null,
    accessibility: null,
    console: null,
    screenshot: null,
    error: null,
    assertions: null,
  }

  client.beginCapture(options.key)
  let pageErrors = []
  try {
    await setViewport(client, options.variant)
    await navigate(client, options.url, options.fixture)
    await client.evaluate(PAGE_INSTALL_ERRORS_FUNCTION)
    await sleep(QUIET_MS)
    result.snapshot = await client.evaluate(PAGE_SNAPSHOT_FUNCTION, options.variant)
    if (options.accessibility) {
      result.accessibility = await getAccessibilitySnapshot(
        client,
        result.snapshot.hiddenChart.itemCount,
      )
    }
    pageErrors = await getPageErrors(client)
    if (options.screenshotPath) {
      result.screenshot = await capturePng(client, options.screenshotPath)
    }
  } catch (error) {
    result.error = errorRecord(error)
  } finally {
    const capture = client.endCapture()
    result.console = mergeConsole(capture, pageErrors)
    result.assertions = scenarioAssertions(result)
    result.status = (
      result.error
      || result.console.errors.length > 0
      || Object.values(result.assertions ?? {}).some((assertion) => assertion === false)
    ) ? 'FAIL' : 'PASS'
  }
  return result
}

function historyGeometry(histories) {
  return (histories ?? []).map((history) => {
    const segments = history.segments ?? []
    const tops = segments.map((segment) => segment?.top ?? NaN)
    const widths = segments.map((segment) => segment?.width ?? NaN)
    const first = segments[0] ?? null
    const last = segments[segments.length - 1] ?? null
    const rowAligned = segments.length > 0
      && Math.max(...tops) - Math.min(...tops) <= GEOMETRY_EPSILON
    const equalCells = segments.length > 0
      && Math.max(...widths) - Math.min(...widths) <= GEOMETRY_EPSILON
    const fullWidth = Boolean(
      history.rect
      && first
      && last
      && Math.abs(first.left - history.rect.left) <= GEOMETRY_EPSILON
      && Math.abs(last.right - history.rect.right) <= GEOMETRY_EPSILON,
    )
    return {
      segmentCount: history.segmentCount,
      rowAligned,
      equalCells,
      fullWidth,
      pass: rowAligned && equalCells && fullWidth,
      historyRect: history.rect,
      segmentRects: segments,
      css: history.css,
    }
  })
}

async function runAttendanceHistory(client, baseOrigin) {
  const variant = { width: 390, height: 844, rootFont: 16, theme: 'dark' }
  const fixture = '4596-365'
  const result = {
    fixture,
    viewport: variant,
    url: harnessUrl(baseOrigin, fixture, variant),
    status: 'FAIL',
    snapshot: null,
    geometry: [],
    console: null,
    error: null,
    expansion: null,
  }
  client.beginCapture('attendance-history')
  let pageErrors = []
  try {
    await setViewport(client, variant)
    await navigate(client, result.url, fixture)
    await client.evaluate(PAGE_INSTALL_ERRORS_FUNCTION)
    await sleep(QUIET_MS)
    result.snapshot = await client.evaluate(PAGE_SNAPSHOT_FUNCTION, variant)
    if (result.snapshot.attendanceHistories.length === 0) {
      result.expansion = await client.evaluate(PAGE_CLICK_FIRST_SUBJECT_FUNCTION)
      if (!result.expansion.ok) {
        throw new Error('Attendance history cards absent and subject expansion unavailable')
      }
      await waitForPageValue(
        client,
        PAGE_SNAPSHOT_FUNCTION,
        variant,
        (snapshot) => snapshot.attendanceHistories.length > 0,
      )
      result.snapshot = await client.evaluate(PAGE_SNAPSHOT_FUNCTION, variant)
    }
    result.geometry = historyGeometry(result.snapshot.attendanceHistories)
    pageErrors = await getPageErrors(client)
  } catch (error) {
    result.error = errorRecord(error)
  } finally {
    const capture = client.endCapture()
    result.console = mergeConsole(capture, pageErrors)
    const geometryPass = result.geometry.length > 0
      && result.geometry.every((entry) => entry.pass)
    result.status = result.error || result.console.errors.length > 0 || !geometryPass
      ? 'FAIL'
      : 'PASS'
  }
  return result
}

async function pressKey(client, key, code, virtualKey) {
  await client.send('Input.dispatchKeyEvent', {
    type: 'rawKeyDown',
    key,
    code,
    windowsVirtualKeyCode: virtualKey,
    nativeVirtualKeyCode: virtualKey,
    ...(key === 'Enter' ? { text: '\r', unmodifiedText: '\r' } : {}),
  })
  await client.send('Input.dispatchKeyEvent', {
    type: 'keyUp',
    key,
    code,
    windowsVirtualKeyCode: virtualKey,
    nativeVirtualKeyCode: virtualKey,
  })
}

async function runKeyboardInline(client, baseOrigin) {
  const variant = { width: 390, height: 844, rootFont: 16, theme: 'dark' }
  const fixture = '4593-848496'
  const result = {
    fixture,
    viewport: variant,
    url: harnessUrl(baseOrigin, fixture, variant, false),
    status: 'FAIL',
    blocked: false,
    steps: [],
    console: null,
    error: null,
  }
  client.beginCapture('attendance-keyboard-inline')
  let pageErrors = []
  try {
    await setViewport(client, variant)
    await navigate(client, result.url, fixture)
    await client.evaluate(PAGE_INSTALL_ERRORS_FUNCTION)
    await sleep(QUIET_MS)
    let details = await client.evaluate(PAGE_ATTENDANCE_REQUEST_DETAILS_FUNCTION)
    if (!details.trigger) {
      result.blocked = true
      result.status = 'BLOCKED'
      result.error = {
        name: 'BlockedSubcheck',
        message: 'No robustly derivable enabled request trigger was rendered',
      }
      return result
    }

    if (!details.enabledAction) {
      const focusTrigger = await client.evaluate(PAGE_FOCUS_REQUEST_TRIGGER_FUNCTION)
      result.steps.push({ id: 'focus-trigger', observed: focusTrigger })
      if (!focusTrigger.ok) {
        result.blocked = true
        result.status = 'BLOCKED'
        result.error = {
          name: 'BlockedSubcheck',
          message: 'Request trigger could not be focused by its data attribute',
        }
        return result
      }
      await pressKey(client, 'Enter', 'Enter', 13)
      details = await waitForPageValue(
        client,
        PAGE_ATTENDANCE_REQUEST_DETAILS_FUNCTION,
        null,
        (next) => next.enabledAction === true,
      )
      result.steps.push({ id: 'open-actions-enter', observed: details })
    } else {
      result.steps.push({ id: 'actions-preopened', observed: details })
    }

    const focusAction = await client.evaluate(PAGE_FOCUS_ENABLED_ACTION_FUNCTION)
    result.steps.push({ id: 'focus-enabled-action', observed: focusAction })
    if (!focusAction.ok) {
      throw new Error('Enabled request action could not receive focus')
    }
    await pressKey(client, 'Enter', 'Enter', 13)
    const inline = await waitForPageValue(
      client,
      PAGE_ATTENDANCE_REQUEST_DETAILS_FUNCTION,
      null,
      (next) => next.inline === true,
    )
    const inlineFocus = await client.evaluate(PAGE_ACTIVE_FOCUS_FUNCTION)
    result.steps.push({ id: 'open-inline-enter', observed: { inline, focus: inlineFocus } })
    if (
      inlineFocus.inline !== true
      || inlineFocus.className?.includes('attendance-back-button') !== true
    ) {
      throw new Error('Inline request did not return focus to attendance back button')
    }

    await pressKey(client, 'Enter', 'Enter', 13)
    const returned = await waitForPageValue(
      client,
      PAGE_ATTENDANCE_REQUEST_DETAILS_FUNCTION,
      null,
      (next) => next.inline === false,
    )
    const returnedFocus = await client.evaluate(PAGE_ACTIVE_FOCUS_FUNCTION)
    result.steps.push({ id: 'back-enter', observed: { returned, focus: returnedFocus } })
    if (!returnedFocus.requestTrigger) {
      throw new Error('Inline request Back did not restore request trigger focus')
    }
    pageErrors = await getPageErrors(client)
    result.status = 'PASS'
  } catch (error) {
    result.error = errorRecord(error)
    result.status = 'FAIL'
  } finally {
    const capture = client.endCapture()
    result.console = mergeConsole(capture, pageErrors)
    if (result.console.errors.length > 0 && result.status === 'PASS') {
      result.status = 'FAIL'
    }
  }
  return result
}

function signatureChanged(before, after) {
  return before.aggregateText !== after.aggregateText
    && before.pathSignature !== after.pathSignature
}

async function runStatisticsInteractions(client, baseOrigin) {
  const variant = { width: 390, height: 844, rootFont: 16, theme: 'dark' }
  const fixture = '4603-848696'
  const result = {
    fixture,
    viewport: variant,
    url: harnessUrl(baseOrigin, fixture, variant),
    status: 'FAIL',
    initial: null,
    afterType: null,
    afterLast: null,
    afterRange: null,
    afterRestore: null,
    actions: [],
    assertions: null,
    console: null,
    error: null,
  }
  client.beginCapture('statistics-interactions')
  let pageErrors = []
  try {
    await setViewport(client, variant)
    await navigate(client, result.url, fixture)
    await client.evaluate(PAGE_INSTALL_ERRORS_FUNCTION)
    await sleep(QUIET_MS)
    result.initial = await client.evaluate(PAGE_STATS_STATE_FUNCTION)
    if (result.initial.selected.length < 2) {
      throw new Error('Statistics interaction fixture did not expose at least two selected types')
    }
    if (result.initial.cards.length < 2) {
      throw new Error('Statistics interaction fixture did not retain at least two type cards')
    }

    const typeClick = await client.evaluate(PAGE_STATS_CLICK_SELECTED_TYPE_FUNCTION)
    result.actions.push({ id: 'deselect-one-type', observed: typeClick })
    if (!typeClick.ok) throw new Error(typeClick.reason)
    result.afterType = await waitForPageValue(
      client,
      PAGE_STATS_STATE_FUNCTION,
      null,
      (state) => state.selected.length === result.initial.selected.length - 1,
    )

    const lastClick = await client.evaluate(PAGE_STATS_CLICK_LAST_TYPE_FUNCTION)
    result.actions.push({ id: 'attempt-last-type', observed: lastClick })
    if (!lastClick.ok) throw new Error(lastClick.reason)
    result.afterLast = await sleep(40).then(() => client.evaluate(PAGE_STATS_STATE_FUNCTION))

    const targetRange = result.afterLast.range === 'Дни' ? 'Недели' : 'Дни'
    const rangeClick = await client.evaluate(PAGE_STATS_CLICK_RANGE_FUNCTION, targetRange)
    result.actions.push({ id: 'change-range', target: targetRange, observed: rangeClick })
    if (!rangeClick.ok) throw new Error(rangeClick.reason)
    result.afterRange = await waitForPageValue(
      client,
      PAGE_STATS_STATE_FUNCTION,
      null,
      (state) => state.range === targetRange,
    )

    const restoreLabel = targetRange === 'Дни' ? 'Недели' : 'Дни'
    const restoreClick = await client.evaluate(PAGE_STATS_CLICK_RANGE_FUNCTION, restoreLabel)
    result.actions.push({ id: 'restore-range', target: restoreLabel, observed: restoreClick })
    if (!restoreClick.ok) throw new Error(restoreClick.reason)
    result.afterRestore = await waitForPageValue(
      client,
      PAGE_STATS_STATE_FUNCTION,
      null,
      (state) => state.range === restoreLabel,
    )
    pageErrors = await getPageErrors(client)
  } catch (error) {
    result.error = errorRecord(error)
  } finally {
    const capture = client.endCapture()
    result.console = mergeConsole(capture, pageErrors)
    const cardsRetained = Boolean(
      result.initial
      && result.afterType
      && result.afterLast
      && JSON.stringify(result.initial.cards) === JSON.stringify(result.afterType.cards)
      && JSON.stringify(result.initial.cards) === JSON.stringify(result.afterLast.cards),
    )
    const lastSelectedDisabled = Boolean(
      result.afterLast
      && result.afterLast.selected.length === 1
      && result.afterLast.types.filter((type) => type.selected).length === 1
      && result.afterLast.types.filter((type) => type.selected && type.disabled).length === 1,
    )
    const projectionChanged = Boolean(
      result.initial
      && result.afterType
      && signatureChanged(result.initial, result.afterType),
    )
    const rangeChanged = Boolean(
      result.afterType
      && result.afterRange
      && result.afterType.pathSignature !== result.afterRange.pathSignature,
    )
    const rangeRestored = Boolean(
      result.afterType
      && result.afterRestore
      && result.afterRestore.pathSignature === result.afterType.pathSignature
      && result.afterRestore.range !== result.afterRange?.range,
    )
    result.assertions = {
      projectionChanged,
      rangeChanged,
      rangeRestored,
      cardsRetained,
      lastSelectedDisabled,
    }
    const assertionsPass = Object.values(result.assertions).every(Boolean)
    result.status = result.error || result.console.errors.length > 0 || !assertionsPass
      ? 'FAIL'
      : 'PASS'
  }
  return result
}

function aggregateAssertions(report) {
  const baseResults = report.fixtures.base
  const matrixResults = report.matrices.results
  const inspected = [...baseResults, ...matrixResults]
  const allResults = [
    ...inspected,
    report.interactions.attendanceHistory,
    report.interactions.statistics,
    report.interactions.keyboardInline,
  ].filter(Boolean)
  const rootPass = baseResults.length === BASE_FIXTURES.length
    && baseResults.every((result) => result.assertions?.root === true)
  const overflowPass = allResults.length > 0
    && allResults.every((result) => result.assertions?.globalOverflow !== false)
    && allResults.every((result) => !result.snapshot || result.snapshot.overflow.global === false)
  const consolePass = allResults.every((result) => (
    result.console?.errors?.length === 0
  ))
  const detail = baseResults.find((result) => result.fixture === '4603-848696')
  const cards = detail?.snapshot?.typeCards ?? []
  const cardsPass = Boolean(
    detail
    && cards.length >= 2
    && cards.every((card) => (
      card.rect
      && card.rect.top >= -GEOMETRY_EPSILON
      && card.rect.bottom <= 844 + GEOMETRY_EPSILON
    )),
  )
  const axPass = Boolean(detail?.accessibility?.meaningfulRussianPointData)
  const history = report.interactions.attendanceHistory
  const historyPass = history?.status === 'PASS'
  const statistics = report.interactions.statistics
  const statisticsPass = statistics?.status === 'PASS'
  const keyboard = report.interactions.keyboardInline
  const keyboardStatus = keyboard?.status === 'BLOCKED' ? 'BLOCKED' : (
    keyboard?.status === 'PASS' ? 'PASS' : 'FAIL'
  )
  const screenshotRecords = report.screenshots
  const screenshotPass = screenshotRecords.length >= 2
    && screenshotRecords.every((screenshot) => screenshot.ok === true)
  const assertions = {
    missing13Roots: {
      status: rootPass ? 'PASS' : 'FAIL',
      criteria: 'All 13 base fixtures render exactly one harness and screen root',
      observed: baseResults.map((result) => ({
        fixture: result.fixture,
        root: result.assertions?.root ?? false,
      })),
    },
    globalOverflow: {
      status: overflowPass ? 'PASS' : 'FAIL',
      criteria: 'Document/body scrollWidth does not exceed document clientWidth',
      observed: inspected.map((result) => ({
        key: result.key,
        fixture: result.fixture,
        overflow: result.snapshot?.overflow ?? null,
      })),
    },
    consoleErrors: {
      status: consolePass ? 'PASS' : 'FAIL',
      criteria: 'No Runtime/Log/window console errors in captured scenarios',
      observed: allResults.map((result) => ({
        key: result.key ?? result.fixture,
        errors: result.console?.errors ?? null,
      })),
    },
    detailCardsWithin844: {
      status: cardsPass ? 'PASS' : 'FAIL',
      criteria: 'Both detail statistics type cards are fully inside 390x844 base viewport',
      observed: cards.map((card) => ({ type: card.type, rect: card.rect })),
    },
    axMeaningfulRussianPointData: {
      status: axPass ? 'PASS' : 'FAIL',
      criteria: 'Hidden chart point details are present and represented by meaningful Russian AX names',
      observed: detail?.accessibility ?? null,
    },
    attendanceHistoryOneRowFullWidth: {
      status: historyPass ? 'PASS' : 'FAIL',
      criteria: '4596-365 attendance history uses equal-width cells in one full-width row',
      observed: history?.geometry ?? null,
    },
    statisticsProjection: {
      status: statisticsPass ? 'PASS' : 'FAIL',
      criteria: 'Type/range controls change supplied aggregate/path, retain cards, and disable last type',
      observed: statistics?.assertions ?? null,
    },
    screenshots: {
      status: screenshotPass ? 'PASS' : 'FAIL',
      criteria: 'Detail 390 dark and overview 320/root24 PNGs are written',
      observed: screenshotRecords,
    },
    keyboardInline: {
      status: keyboardStatus,
      criteria: 'Keyboard request focus returns to Back and then request trigger; explicit BLOCKED is allowed when selectors cannot be derived',
      observed: keyboard,
    },
  }
  const hardFailures = Object.entries(assertions)
    .filter(([id, assertion]) => id !== 'keyboardInline' && assertion.status !== 'PASS')
    .map(([id]) => id)
  if (keyboardStatus === 'FAIL') hardFailures.push('keyboardInline')
  const overallPass = hardFailures.length === 0
  assertions.overall = {
    status: overallPass ? 'PASS' : 'FAIL',
    failures: hardFailures,
  }
  return assertions
}

function createReport(options, startedAt, cdpOrigin, baseOrigin) {
  return {
    schema: 'rct.fe13-cdp-qa.v1',
    startedAt,
    finishedAt: null,
    durationMs: null,
    environment: {
      node: process.version,
      platform: process.platform,
      arch: process.arch,
      cwd: process.cwd(),
      script: SCRIPT_PATH,
      cdpEndpoint: cdpOrigin,
      baseUrl: baseOrigin,
      outputDirectory: path.resolve(options.out),
      policy: 'localhost-only; one created target; no runtime start; no external network',
    },
    runtimeEvidence: {
      cdpDomains: USED_CDP_DOMAINS,
      targetCreated: false,
      targetId: null,
      targetType: null,
      browser: null,
      close: null,
    },
    fixtures: {
      baseIds: BASE_FIXTURES,
      baseViewport: { width: 390, height: 844, rootFont: 16, theme: 'dark' },
      base: [],
    },
    matrices: {
      fixtureIds: RESPONSIVE_FIXTURES,
      variants: RESPONSIVE_VARIANTS,
      results: [],
    },
    interactions: {
      attendanceHistory: null,
      statistics: null,
      keyboardInline: null,
    },
    screenshots: [],
    assertions: null,
    checks: {
      runtime: {
        command: 'node ' + path.basename(SCRIPT_PATH),
        exitCode: null,
        status: 'PENDING',
      },
      syntax: {
        command: 'node --check ' + path.basename(SCRIPT_PATH),
        exitCode: null,
        status: 'ROOT_REQUIRED',
      },
    },
    limitations: [
      'This runner does not start Vite, Chrome, or any product runtime.',
      'No CUA, Playwright, Puppeteer, shell spawning, external network, or data transmission is used.',
      'Keyboard subcheck may be explicitly BLOCKED only when robust DOM selectors are absent.',
    ],
    fatalError: null,
  }
}

async function main() {
  let options
  try {
    options = parseArgs(process.argv.slice(2))
  } catch (error) {
    console.error(errorText(error))
    console.error(usage())
    process.exitCode = 2
    return
  }
  if (options.help) {
    console.log(usage())
    return
  }

  const startedAt = new Date().toISOString()
  let cdpOrigin
  let baseOrigin
  try {
    cdpOrigin = localOrigin(options.cdp, '--cdp')
    baseOrigin = localOrigin(options.base, '--base')
  } catch (error) {
    console.error(errorText(error))
    process.exitCode = 2
    return
  }

  const report = createReport(options, startedAt, cdpOrigin, baseOrigin)
  const outputDirectory = path.resolve(options.out)
  const outputPath = path.join(outputDirectory, JSON_NAME)
  let client = null
  let target = null

  try {
    await fs.mkdir(outputDirectory, { recursive: true })
    const version = await fetchJson(cdpOrigin + '/json/version')
    report.runtimeEvidence.browser = {
      browser: version.Browser ?? null,
      protocolVersion: version['Protocol-Version'] ?? null,
      userAgent: version['User-Agent'] ?? null,
    }
    const firstVariant = { width: 390, height: 844, rootFont: 16, theme: 'dark' }
    target = await createTarget(
      cdpOrigin,
      harnessUrl(baseOrigin, BASE_FIXTURES[0], firstVariant),
    )
    report.runtimeEvidence.targetCreated = true
    report.runtimeEvidence.targetId = target.id
    report.runtimeEvidence.targetType = target.type ?? null
    client = await CdpClient.connect(target.webSocketDebuggerUrl)
    attachProtocolCapture(client)
    await enableDomains(client)

    for (const fixture of BASE_FIXTURES) {
      const variant = { width: 390, height: 844, rootFont: 16, theme: 'dark' }
      const screenshotPath = fixture === '4603-848696'
        ? path.join(outputDirectory, 'student-academic-ui-detail-390x844-root16-dark.png')
        : null
      report.fixtures.base.push(await inspectScenario(client, {
        key: 'base-' + fixture,
        fixture,
        variant,
        terminal: true,
        url: harnessUrl(baseOrigin, fixture, variant),
        accessibility: fixture === '4603-848696',
        screenshotPath,
      }))
      if (screenshotPath) {
        const scenario = report.fixtures.base[report.fixtures.base.length - 1]
        report.screenshots.push({
          id: 'detail390dark',
          path: screenshotPath,
          ok: Boolean(scenario.screenshot),
          bytes: scenario.screenshot?.bytes ?? null,
          error: scenario.error?.message ?? null,
        })
      }
    }

    for (const fixture of RESPONSIVE_FIXTURES) {
      for (const variant of RESPONSIVE_VARIANTS) {
        const isOverviewScreenshot = fixture === '4603-142'
          && variant.width === 320
          && variant.rootFont === 24
          && variant.theme === 'dark'
        const screenshotPath = isOverviewScreenshot
          ? path.join(outputDirectory, 'student-academic-ui-overview-320x844-root24-dark.png')
          : null
        const key = [
          'responsive',
          fixture,
          variant.width + 'x' + variant.height,
          'root' + variant.rootFont,
          variant.theme,
        ].join('-')
        report.matrices.results.push(await inspectScenario(client, {
          key,
          fixture,
          variant,
          terminal: true,
          url: harnessUrl(baseOrigin, fixture, variant),
          accessibility: false,
          screenshotPath,
        }))
        if (screenshotPath) {
          const scenario = report.matrices.results[report.matrices.results.length - 1]
          report.screenshots.push({
            id: 'overview320root24',
            path: screenshotPath,
            ok: Boolean(scenario.screenshot),
            bytes: scenario.screenshot?.bytes ?? null,
            error: scenario.error?.message ?? null,
          })
        }
      }
    }

    report.interactions.attendanceHistory = await runAttendanceHistory(client, baseOrigin)
    report.interactions.statistics = await runStatisticsInteractions(client, baseOrigin)
    report.interactions.keyboardInline = await runKeyboardInline(client, baseOrigin)
  } catch (error) {
    report.fatalError = errorRecord(error)
  } finally {
    if (client) {
      try {
        await client.send('Emulation.clearDeviceMetricsOverride')
      } catch {
        // The target may already have gone away.
      }
      try {
        await client.close()
      } catch (error) {
        report.runtimeEvidence.websocketCloseError = errorRecord(error)
      }
    }
    if (target && cdpOrigin) {
      try {
        report.runtimeEvidence.close = await closeTarget(cdpOrigin, target.id)
      } catch (error) {
        report.runtimeEvidence.close = {
          ok: false,
          error: errorRecord(error),
        }
      }
    }
    report.finishedAt = new Date().toISOString()
    report.durationMs = Date.parse(report.finishedAt) - Date.parse(report.startedAt)
    report.assertions = aggregateAssertions(report)
    report.checks.runtime.status = report.assertions.overall.status
    report.checks.runtime.exitCode = report.assertions.overall.status === 'PASS' ? 0 : 1
    try {
      await fs.mkdir(outputDirectory, { recursive: true })
      await fs.writeFile(outputPath, JSON.stringify(report, null, 2) + '\n', 'utf8')
    } catch (error) {
      console.error('Unable to write report: ' + errorText(error))
      process.exitCode = 1
      return
    }
  }

  console.log(JSON.stringify({
    report: outputPath,
    status: report.assertions.overall.status,
    failures: report.assertions.overall.failures,
    keyboardInline: report.assertions.keyboardInline.status,
  }))
  process.exitCode = report.assertions.overall.status === 'PASS' ? 0 : 1
}

main().catch((error) => {
  console.error(errorText(error))
  process.exitCode = 1
})
