import axios from 'axios'

const TOKENS = 'payvault.tokens'

export const tokenStore = {
  get() {
    try { return JSON.parse(localStorage.getItem(TOKENS)) } catch { return null }
  },
  set(t) { localStorage.setItem(TOKENS, JSON.stringify(t)) },
  clear() { localStorage.removeItem(TOKENS) },
}

// Every call goes to the API Gateway (proxied from /api in dev and in Docker).
const api = axios.create({ baseURL: '/api', timeout: 15000 })

api.interceptors.request.use((config) => {
  const t = tokenStore.get()
  if (t?.accessToken) config.headers.Authorization = `Bearer ${t.accessToken}`
  return config
})

let refreshing = null

// On 401, try the refresh token once, then replay the original request.
api.interceptors.response.use(
  (res) => res,
  async (error) => {
    const original = error.config
    const t = tokenStore.get()
    const isAuthCall = original?.url?.startsWith('/auth/')
    if (error.response?.status === 401 && t?.refreshToken && !original._retried && !isAuthCall) {
      original._retried = true
      try {
        refreshing = refreshing || axios.post('/api/auth/refresh', { refreshToken: t.refreshToken })
        const { data } = await refreshing
        tokenStore.set(data)
        original.headers.Authorization = `Bearer ${data.accessToken}`
        return api(original)
      } catch (e) {
        tokenStore.clear()
        window.location.href = '/login'
      } finally {
        refreshing = null
      }
    }
    return Promise.reject(error)
  }
)

export function errorMessage(err) {
  return err?.response?.data?.error || err?.message || 'Something went wrong'
}

export function newIdempotencyKey() {
  return crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

export default api
