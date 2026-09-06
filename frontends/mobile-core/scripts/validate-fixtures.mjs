import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'

// Task-local conformance subset used by the committed r1 schemas: local $ref,
// discriminator/oneOf/allOf, nullable, required/properties, typed maps/arrays,
// enum, scalar type/format/pattern/length and numeric bounds. It is deliberately
// not a general OpenAPI or JSON Schema validator; integration must validate the
// same fixtures against actual HTTP responses and the canonical snapshot.
const specUrl = new URL('../../../docs/openapi/mobile-bff.json', import.meta.url)
const manifestUrl = new URL('../fixtures/manifest.json', import.meta.url)

function resolveRef(root, ref) {
  if (!ref.startsWith('#/')) {
    throw new Error(
      'External ref is not allowed in contract fixtures: ' + ref,
    )
  }
  return ref
    .slice(2)
    .split('/')
    .map((part) => part.replaceAll('~1', '/').replaceAll('~0', '~'))
    .reduce((node, part) => node?.[part], root)
}

function validationError(root, schema, value, path = '$', seen = new Set()) {
  if (schema?.$ref) {
    const marker = schema.$ref + '@' + path
    if (seen.has(marker)) return null
    const nextSeen = new Set(seen).add(marker)
    const resolved = resolveRef(root, schema.$ref)
    if (!resolved) return path + ': unresolved ref ' + schema.$ref
    return validationError(root, resolved, value, path, nextSeen)
  }

  if (value === null) {
    return schema?.nullable === true || schema?.type === 'null'
      ? null
      : path + ': null is not allowed'
  }

  if (Array.isArray(schema?.allOf)) {
    for (const child of schema.allOf) {
      const error = validationError(root, child, value, path, seen)
      if (error) return error
    }
  }

  if (Array.isArray(schema?.oneOf)) {
    const discriminatorName = schema.discriminator?.propertyName
    const discriminatorValue =
      discriminatorName && typeof value === 'object'
        ? value?.[discriminatorName]
        : undefined
    const mappedRef = schema.discriminator?.mapping?.[discriminatorValue]
    if (mappedRef) {
      return validationError(root, { $ref: mappedRef }, value, path, seen)
    }
    const matches = schema.oneOf.filter(
      (child) => validationError(root, child, value, path, seen) === null,
    )
    if (matches.length !== 1) {
      return (
        path +
        ': expected exactly one oneOf branch, matched ' +
        matches.length
      )
    }
  }

  if (schema?.enum && !schema.enum.includes(value)) {
    return path + ': ' + JSON.stringify(value) + ' is outside enum'
  }

  switch (schema?.type) {
    case 'object': {
      if (typeof value !== 'object' || Array.isArray(value)) {
        return path + ': expected object'
      }
      for (const name of schema.required ?? []) {
        if (!Object.hasOwn(value, name)) return path + '.' + name + ': required'
      }
      for (const [name, child] of Object.entries(schema.properties ?? {})) {
        if (!Object.hasOwn(value, name)) continue
        const error = validationError(
          root,
          child,
          value[name],
          path + '.' + name,
          seen,
        )
        if (error) return error
      }
      if (
        schema.additionalProperties &&
        typeof schema.additionalProperties === 'object'
      ) {
        const declared = new Set(Object.keys(schema.properties ?? {}))
        for (const [name, item] of Object.entries(value)) {
          if (declared.has(name)) continue
          const error = validationError(
            root,
            schema.additionalProperties,
            item,
            path + '.' + name,
            seen,
          )
          if (error) return error
        }
      }
      return null
    }
    case 'array': {
      if (!Array.isArray(value)) return path + ': expected array'
      for (let index = 0; index < value.length; index += 1) {
        const error = validationError(
          root,
          schema.items ?? {},
          value[index],
          path + '[' + index + ']',
          seen,
        )
        if (error) return error
      }
      return null
    }
    case 'integer':
      if (!Number.isInteger(value)) return path + ': expected integer'
      break
    case 'number':
      if (typeof value !== 'number' || !Number.isFinite(value)) {
        return path + ': expected finite number'
      }
      break
    case 'boolean':
      if (typeof value !== 'boolean') return path + ': expected boolean'
      break
    case 'string':
      if (typeof value !== 'string') return path + ': expected string'
      if (schema.pattern && !new RegExp(schema.pattern).test(value)) {
        return path + ': does not match ' + schema.pattern
      }
      if (schema.minLength !== undefined && value.length < schema.minLength) {
        return path + ': shorter than ' + schema.minLength
      }
      if (schema.maxLength !== undefined && value.length > schema.maxLength) {
        return path + ': longer than ' + schema.maxLength
      }
      if (schema.format === 'date' && !/^\d{4}-\d{2}-\d{2}$/.test(value)) {
        return path + ': expected ISO date'
      }
      if (
        schema.format === 'date-time' &&
        (!/[zZ]|[+-]\d{2}:\d{2}$/.test(value) ||
          Number.isNaN(Date.parse(value)))
      ) {
        return path + ': expected offset-aware date-time'
      }
      break
    default:
      break
  }

  if (typeof value === 'number') {
    if (schema.minimum !== undefined && value < schema.minimum) {
      return path + ': below minimum ' + schema.minimum
    }
    if (schema.maximum !== undefined && value > schema.maximum) {
      return path + ': above maximum ' + schema.maximum
    }
  }

  return null
}

export async function loadContract() {
  const [root, manifest] = await Promise.all([
    readFile(specUrl, 'utf8').then(JSON.parse),
    readFile(manifestUrl, 'utf8').then(JSON.parse),
  ])
  return { root, manifest }
}

export async function validateFixture(schemaName, value) {
  const { root } = await loadContract()
  const schema = root.components?.schemas?.[schemaName]
  if (!schema) throw new Error('Missing OpenAPI schema: ' + schemaName)
  return validationError(root, schema, value)
}

export async function validateAllFixtures() {
  const { root, manifest } = await loadContract()
  const errors = []
  for (const item of manifest.fixtures) {
    const schema = root.components?.schemas?.[item.schema]
    if (!schema) {
      errors.push(item.file + ': missing schema ' + item.schema)
      continue
    }
    const url = new URL('../fixtures/' + item.file, import.meta.url)
    const value = JSON.parse(await readFile(url, 'utf8'))
    const error = validationError(root, schema, value)
    if (error) errors.push(item.file + ': ' + error)
  }
  return errors
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const errors = await validateAllFixtures()
  if (errors.length > 0) {
    throw new Error(
      'Fixture conformance failed:\n' + errors.join('\n'),
    )
  }
  const { manifest } = await loadContract()
  console.log(
    'Validated ' +
      manifest.fixtures.length +
      ' fixtures against JS-STUDENT-01-r1',
  )
}
