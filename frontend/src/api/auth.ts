import { request } from './client'
import type { AuthResponse, LoginRequest, RegisterRequest, User } from './types'

export const authApi = {
  register: (body: RegisterRequest) => request<AuthResponse>('/auth/register', { method: 'POST', body }),
  login: (body: LoginRequest) => request<AuthResponse>('/auth/login', { method: 'POST', body }),
  /** Succeeds only while the stored token is still valid; otherwise the client logs the user out. */
  me: () => request<User>('/auth/me'),
}
