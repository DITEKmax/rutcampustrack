import type {
  AdminMapBuildingResponse,
  AdminMapDeleteRequest,
  AdminMapDeletionPreview,
  AdminMapDeletionResult,
  AdminMapDeletionTarget,
  AdminMapFloorResponse,
  AdminMapPlan,
  AdminMapPlanTarget,
  AdminMapPlanDeleteRequest,
  AdminMapPlanDeletionPreview,
  AdminMapPlanDeletionResult,
  AdminMapPlanUploadRequest,
  AdminMapPlanUploadResult,
  MapFormat,
  MapManifest,
  MapPlan,
  MobileProblemDetails,
} from './types'
import { StaleSessionGenerationError } from '../shared/session-owner'

export interface MapApiOptions {
  accessToken: () => string | null
  onUnauthorized?: () => Promise<void>
  currentGeneration?: () => number
  fetcher?: typeof fetch
}

export class MapApiError extends Error {
  readonly problem: MobileProblemDetails | null

  constructor(readonly response: Response, problem: MobileProblemDetails | null) {
    super(problem?.detail || problem?.title || `HTTP ${response.status}`)
    this.name = 'MapApiError'
    this.problem = problem
  }
}

/** Dedicated role-scoped reader for the canonical /api/v1/map surface. */
export class CampusMapClient {
  private readonly fetcher: typeof fetch

  constructor(private readonly options: MapApiOptions) {
    this.fetcher = options.fetcher ?? ((input, init) => globalThis.fetch(input, init))
  }

  currentGeneration(): number | undefined {
    return this.options.currentGeneration?.()
  }

  isCurrentGeneration(generation: number | undefined): boolean {
    return generation === undefined
      || this.options.currentGeneration?.() === generation
  }

  async getManifest(etag?: string, signal?: AbortSignal): Promise<{
    data: MapManifest | null
    etag: string | null
  }> {
    const response = await this.requestResponse('/api/v1/map/manifest', {
      ...(etag ? { headers: { 'If-None-Match': etag } } : {}),
      ...(signal ? { signal } : {}),
    })
    if (response.status === 304) {
      await response.arrayBuffer()
      return { data: null, etag: response.headers.get('ETag') }
    }
    return {
      data: await this.json<MapManifest>(response),
      etag: response.headers.get('ETag'),
    }
  }

  async getFloorPlan(buildingId: string, floorId: string, signal?: AbortSignal): Promise<MapPlan | null> {
    const response = await this.requestResponse(
      `/api/v1/map/buildings/${encodeURIComponent(buildingId)}/floors/${encodeURIComponent(floorId)}/plan`,
      signal ? { signal } : undefined,
    )
    if (response.status === 204) {
      await response.arrayBuffer()
      return null
    }
    const body = await this.json<{ plan: MapPlan | null }>(response)
    return body.plan
  }

  async downloadAsset(
    buildingId: string,
    floorId: string,
    version: string,
    format: MapFormat,
    assetId: string,
    signal?: AbortSignal,
  ): Promise<Blob> {
    const generation = this.currentGeneration()
    const response = await this.requestResponse(
      `/api/v1/map/buildings/${encodeURIComponent(buildingId)}/floors/${encodeURIComponent(floorId)}`
        + `/plans/${encodeURIComponent(version)}/assets/${format}/${encodeURIComponent(assetId)}`,
      {
        headers: { Accept: format === 'png' ? 'image/png' : 'image/svg+xml' },
        ...(signal ? { signal } : {}),
      },
    )
    const blob = await response.blob()
    this.assertCurrentGeneration(generation)
    const expectedType = format === 'png' ? 'image/png' : 'image/svg+xml'
    if (blob.size === 0 || blob.type.split(';', 1)[0]?.toLowerCase() !== expectedType) {
      throw new Error('Сервер вернул некорректный файл схемы.')
    }
    return blob
  }

  async recordFloorOpen(
    buildingId: string,
    floorId: string,
    intentId: string,
    signal?: AbortSignal,
  ): Promise<void> {
    const response = await this.requestResponse(
      `/api/v1/map/buildings/${encodeURIComponent(buildingId)}/floors/${encodeURIComponent(floorId)}/opens`,
      {
        method: 'POST',
        headers: {
          'Idempotency-Key': intentId,
        },
        ...(signal ? { signal } : {}),
      },
    )
    await response.arrayBuffer()
  }

  private async requestResponse(path: string, init?: RequestInit, retried = false): Promise<Response> {
    const generation = this.currentGeneration()
    this.assertCurrentGeneration(generation)
    const token = this.options.accessToken()
    const headers = new Headers(init?.headers)
    if (!headers.has('Accept')) headers.set('Accept', 'application/json')
    if (token) headers.set('Authorization', `Bearer ${token}`)
    const response = await this.fetcher(path, { ...init, headers, credentials: 'include' })
    this.assertCurrentGeneration(generation)
    if (response.ok || response.status === 304) return response
    if (response.status === 401 && !retried && this.options.onUnauthorized) {
      await this.options.onUnauthorized()
      this.assertCurrentGeneration(generation)
      return this.requestResponse(path, init, true)
    }
    let problem: MobileProblemDetails | null = null
    try {
      problem = await response.json() as MobileProblemDetails
    } catch {
      // Keep the response status when a gateway cannot return Problem Details.
    }
    throw new MapApiError(response, problem)
  }

  private assertCurrentGeneration(generation: number | undefined): void {
    if (!this.isCurrentGeneration(generation)) throw new StaleSessionGenerationError()
  }

  private async json<T>(response: Response): Promise<T> {
    return response.json() as Promise<T>
  }
}

/** Dedicated ADMIN inventory/version client; upload uses multipart fields only. */
export class AdminMapClient {
  private static readonly basePath = '/api/academic/map'
  private readonly fetcher: typeof fetch

  constructor(private readonly options: MapApiOptions) {
    this.fetcher = options.fetcher ?? ((input, init) => globalThis.fetch(input, init))
  }

  currentGeneration(): number | undefined {
    return this.options.currentGeneration?.()
  }

  isCurrentGeneration(generation: number | undefined): boolean {
    return generation === undefined || this.currentGeneration() === generation
  }

  private codeQuery(buildingCode: string, floorCode: string, format?: MapFormat): string {
    if (![buildingCode, floorCode].every(code => /^[1-9][0-9]*$/.test(code) && code.length <= 64)
      || (format !== undefined && format !== 'png' && format !== 'svg')) throw new TypeError('Укажи положительные номера корпуса и этажа.')
    return new URLSearchParams({ buildingCode, floorCode, ...(format ? { format } : {}) }).toString()
  }

  async resolvePlanTarget(buildingCode: string, floorCode: string, signal?: AbortSignal): Promise<AdminMapPlanTarget> {
    const result = await this.request<AdminMapPlanTarget>(`${AdminMapClient.basePath}/plans/target?${this.codeQuery(buildingCode, floorCode)}`, { cache: 'no-store', ...(signal ? { signal } : {}) })
    if (!result || result.buildingCode !== buildingCode || result.floorCode !== floorCode
      || (result.status !== 'found' && result.status !== 'absent')
      || (result.status === 'absent' ? result.floor !== null : !result.floor || result.floor.code !== floorCode)) throw new TypeError('Ответ относится к другой схеме.')
    return result
  }

  async planDeletionPreview(buildingCode: string, floorCode: string, format?: MapFormat, signal?: AbortSignal): Promise<AdminMapPlanDeletionPreview> {
    const result = await this.request<AdminMapPlanDeletionPreview>(`${AdminMapClient.basePath}/plans/target/deletion-preview?${this.codeQuery(buildingCode, floorCode, format)}`, { cache: 'no-store', ...(signal ? { signal } : {}) })
    if (!result || result.buildingCode !== buildingCode || result.floorCode !== floorCode || result.format !== (format ?? null)
      || typeof result.label !== 'string' || !Number.isSafeInteger(result.files) || result.files < 0
      || !Number.isSafeInteger(result.bytes) || result.bytes < 0 || !/^[0-9a-f]{64}$/.test(result.previewDigest)) throw new TypeError('Предпросмотр удаления не подтверждён.')
    return result
  }

  async deletePlanTarget(payload: AdminMapPlanDeleteRequest): Promise<AdminMapPlanDeletionResult> {
    this.codeQuery(payload.buildingCode, payload.floorCode, payload.format)
    const result = await this.request<AdminMapPlanDeletionResult>(`${AdminMapClient.basePath}/plans/target/deletion`, { method: 'POST', body: JSON.stringify(payload) }, false)
    if (!result || result.operationId !== payload.operationId || result.buildingCode !== payload.buildingCode
      || result.floorCode !== payload.floorCode || result.format !== (payload.format ?? null) || result.status !== 'COMPLETED') throw new TypeError('Подтверждение удаления не получено.')
    return result
  }

  async updateBuilding(id: string, code: string, label: string): Promise<AdminMapBuildingResponse> {
    return this.request(`${AdminMapClient.basePath}/buildings/${encodeURIComponent(id)}`, {
      method: 'PUT', body: JSON.stringify({ code, label: label || null }),
    })
  }

  async updateFloor(id: string, code: string, label: string): Promise<AdminMapFloorResponse> {
    return this.request(`${AdminMapClient.basePath}/floors/${encodeURIComponent(id)}`, {
      method: 'PUT', body: JSON.stringify({ code, label: label || null }),
    })
  }

  async deletionPreview(type: AdminMapDeletionTarget, id: string): Promise<AdminMapDeletionPreview> {
    return this.request(`${this.targetPath(type, id)}/deletion-preview`)
  }

  async deleteInventory(type: AdminMapDeletionTarget, id: string, payload: AdminMapDeleteRequest): Promise<AdminMapDeletionResult> {
    return this.request(`${this.targetPath(type, id)}/deletion`, {
      method: 'POST', body: JSON.stringify(payload),
    }, false)
  }

  private targetPath(type: AdminMapDeletionTarget, id: string): string {
    return `${AdminMapClient.basePath}/${type === 'FLOOR' ? 'floors' : 'buildings'}/${encodeURIComponent(id)}`
  }

  async listBuildings(signal?: AbortSignal): Promise<readonly AdminMapBuildingResponse[]> {
    return this.request<AdminMapBuildingResponse[]>(`${AdminMapClient.basePath}/buildings`, signal ? { signal } : undefined)
  }

  async listFloors(buildingId?: string, signal?: AbortSignal): Promise<readonly AdminMapFloorResponse[]> {
    const query = buildingId ? `?buildingId=${encodeURIComponent(buildingId)}` : ''
    return this.request<AdminMapFloorResponse[]>(`${AdminMapClient.basePath}/floors${query}`, signal ? { signal } : undefined)
  }

  async createBuilding(code: string, label?: string): Promise<AdminMapBuildingResponse> {
    return this.request<AdminMapBuildingResponse>(`${AdminMapClient.basePath}/buildings`, {
      method: 'POST',
      body: JSON.stringify({ code, label: label || null }),
    }, false)
  }

  async createFloor(buildingId: string, code: string, label?: string): Promise<AdminMapFloorResponse> {
    return this.request<AdminMapFloorResponse>(`${AdminMapClient.basePath}/floors`, {
      method: 'POST',
      body: JSON.stringify({ buildingId, code, label: label || null }),
    }, false)
  }

  async uploadPlan(payload: AdminMapPlanUploadRequest): Promise<AdminMapPlanUploadResult> {
    if (![payload.buildingCode, payload.floorCode].every(code => /^[1-9][0-9]*$/.test(code) && code.length <= 64)) {
      throw new TypeError('Укажи положительные номера корпуса и этажа: до 64 цифр без пробелов.')
    }
    if (payload.label !== undefined && payload.label.length > 255) throw new TypeError('Название схемы слишком длинное.')
    if (!payload.png && !payload.svg) throw new TypeError('Приложи PNG или SVG.')
    const body = new FormData()
    body.append('buildingCode', payload.buildingCode)
    body.append('floorCode', payload.floorCode)
    if (payload.label !== undefined) body.append('label', payload.label)
    if (payload.png) body.append('png', payload.png, payload.png.name)
    if (payload.svg) body.append('svg', payload.svg, payload.svg.name)
    const generation = this.currentGeneration()
    // Publication commands must never replay after a lost acknowledgement or 401.
    const response = await this.requestResponse(`${AdminMapClient.basePath}/plans`, { method: 'POST', body }, false, generation, false)
    const result = await response.json() as AdminMapPlanUploadResult
    this.assertCurrentGeneration(generation)
    if (!result || !['created', 'replaced'].includes(result.status)
      || response.status !== (result.status === 'created' ? 201 : 200)
      || typeof result.buildingCreated !== 'boolean' || typeof result.floorCreated !== 'boolean'
      || (result.status === 'replaced' && (result.buildingCreated || result.floorCreated))
      || (result.buildingCreated && !result.floorCreated)
      || !result.plan || ![result.plan.buildingId, result.plan.floorId, result.plan.version, result.plan.catalogRevision]
        .every(value => typeof value === 'string' && value.length > 0)
      || !(['png', 'svg'] as const).every(format => {
        const slot = result.plan[format]
        return slot && slot.format === format && ['absent', 'processing', 'ready', 'failed'].includes(slot.state)
          && slot.contentType === (format === 'png' ? 'image/png' : 'image/svg+xml')
          && (slot.state !== 'ready' || (typeof slot.id === 'string' && slot.id.length > 0 && slot.bytes > 0))
      })) {
      throw new TypeError('Подтверждение публикации схемы не получено.')
    }
    return result
  }

  async uploadVersion(
    floorId: string,
    payload: { label?: string; png?: File; svg?: File },
  ): Promise<AdminMapPlan> {
    if (!payload.png && !payload.svg) throw new TypeError('Attach a PNG or SVG map file')
    const body = new FormData()
    if (payload.label) body.append('label', payload.label)
    if (payload.png) body.append('png', payload.png, payload.png.name)
    if (payload.svg) body.append('svg', payload.svg, payload.svg.name)
    return this.request<AdminMapPlan>(`${AdminMapClient.basePath}/floors/${encodeURIComponent(floorId)}/versions`, {
      method: 'POST',
      body,
    }, false)
  }

  async getVersion(floorId: string, version: string, signal?: AbortSignal): Promise<AdminMapPlan> {
    return this.request<AdminMapPlan>(
      `${AdminMapClient.basePath}/floors/${encodeURIComponent(floorId)}/versions/${encodeURIComponent(version)}`,
      signal ? { signal } : undefined,
    )
  }

  async downloadAsset(floorId: string, version: string, format: MapFormat, assetId: string): Promise<Blob> {
    const generation = this.currentGeneration()
    const response = await this.requestResponse(
      `${AdminMapClient.basePath}/floors/${encodeURIComponent(floorId)}/versions/${encodeURIComponent(version)}`
        + `/assets/${format}/${encodeURIComponent(assetId)}`,
      { headers: { Accept: format === 'png' ? 'image/png' : 'image/svg+xml' } },
    )
    const body = await response.blob()
    this.assertCurrentGeneration(generation)
    return body
  }

  private async request<T>(path: string, init?: RequestInit, allowRetry = true): Promise<T> {
    const generation = this.currentGeneration()
    const response = await this.requestResponse(path, init, false, generation, allowRetry)
    const body = await response.json() as T
    this.assertCurrentGeneration(generation)
    return body
  }

  private async requestResponse(path: string, init?: RequestInit, retried = false,
    generation = this.currentGeneration(), allowRetry = true): Promise<Response> {
    this.assertCurrentGeneration(generation)
    const token = this.options.accessToken()
    const headers = new Headers(init?.headers)
    if (!headers.has('Accept')) headers.set('Accept', 'application/json')
    if (typeof init?.body === 'string' && !headers.has('Content-Type')) {
      headers.set('Content-Type', 'application/json')
    }
    if (token) headers.set('Authorization', `Bearer ${token}`)
    const response = await this.fetcher(path, { ...init, headers, credentials: 'include' })
    this.assertCurrentGeneration(generation)
    if (response.ok) return response
    if (response.status === 401 && allowRetry && !retried && this.options.onUnauthorized) {
      await this.options.onUnauthorized()
      this.assertCurrentGeneration(generation)
      return this.requestResponse(path, init, true, generation, allowRetry)
    }
    let problem: MobileProblemDetails | null = null
    try {
      problem = await response.json() as MobileProblemDetails
    } catch {
      // Keep the response status when a gateway cannot return Problem Details.
    }
    this.assertCurrentGeneration(generation)
    throw new MapApiError(response, problem)
  }

  private assertCurrentGeneration(generation: number | undefined): void {
    if (!this.isCurrentGeneration(generation)) throw new StaleSessionGenerationError()
  }
}
