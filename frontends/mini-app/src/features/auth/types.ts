export interface AuthUser {
  id: number
  role: string
  groupId?: number
}

export interface TmaAuthResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
}
