import type {
  AdminMapBuildingResponse,
  AdminMapFloorResponse,
  AdminMapPlan,
  MapFormat,
  MapManifest,
  MapPlan,
  MobileProblemDetails,
} from './types'

export interface MapApiOptions {
  accessToken: () => string | null
  onUnauthorized?: () => Promise<void>
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
    const response = await this.requestResponse(
      `/api/v1/map/buildings/${encodeURIComponent(buildingId)}/floors/${encodeURIComponent(floorId)}`
        + `/plans/${encodeURIComponent(version)}/assets/${format}/${encodeURIComponent(assetId)}`,
      {
        headers: { Accept: format === 'png' ? 'image/png' : 'image/svg+xml' },
        ...(signal ? { signal } : {}),
      },
    )
    return response.blob()
  }

  private async requestResponse(path: string, init?: RequestInit, retried = false): Promise<Response> {
    const token = this.options.accessToken()
    const headers = new Headers(init?.headers)
    if (!headers.has('Accept')) headers.set('Accept', 'application/json')
    if (token) headers.set('Authorization', `Bearer ${token}`)
    const response = await this.fetcher(path, { ...init, headers, credentials: 'include' })
    if (response.ok || response.status === 304) return response
    if (response.status === 401 && !retried && this.options.onUnauthorized) {
      await this.options.onUnauthorized()
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
    })
  }

  async createFloor(buildingId: string, code: string, label?: string): Promise<AdminMapFloorResponse> {
    return this.request<AdminMapFloorResponse>(`${AdminMapClient.basePath}/floors`, {
      method: 'POST',
      body: JSON.stringify({ buildingId, code, label: label || null }),
    })
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
    })
  }

  async getVersion(floorId: string, version: string, signal?: AbortSignal): Promise<AdminMapPlan> {
    return this.request<AdminMapPlan>(
      `${AdminMapClient.basePath}/floors/${encodeURIComponent(floorId)}/versions/${encodeURIComponent(version)}`,
      signal ? { signal } : undefined,
    )
  }

  async downloadAsset(floorId: string, version: string, format: MapFormat, assetId: string): Promise<Blob> {
    const response = await this.requestResponse(
      `${AdminMapClient.basePath}/floors/${encodeURIComponent(floorId)}/versions/${encodeURIComponent(version)}`
        + `/assets/${format}/${encodeURIComponent(assetId)}`,
      { headers: { Accept: format === 'png' ? 'image/png' : 'image/svg+xml' } },
    )
    return response.blob()
  }

  private async request<T>(path: string, init?: RequestInit): Promise<T> {
    const response = await this.requestResponse(path, init)
    return response.json() as Promise<T>
  }

  private async requestResponse(path: string, init?: RequestInit, retried = false): Promise<Response> {
    const token = this.options.accessToken()
    const headers = new Headers(init?.headers)
    if (!headers.has('Accept')) headers.set('Accept', 'application/json')
    if (typeof init?.body === 'string' && !headers.has('Content-Type')) {
      headers.set('Content-Type', 'application/json')
    }
    if (token) headers.set('Authorization', `Bearer ${token}`)
    const response = await this.fetcher(path, { ...init, headers, credentials: 'include' })
    if (response.ok) return response
    if (response.status === 401 && !retried && this.options.onUnauthorized) {
      await this.options.onUnauthorized()
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
}
