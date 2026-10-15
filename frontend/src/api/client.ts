// Every call the generated API client makes goes through apiFetch (configured in orval.config.ts).
// It adds the access token, renews it when it expires, and turns error answers into ApiError.
//
// Tokens (see requirements, "Authentication"):
// - access token: lives only in this module's memory (never in localStorage, where any script could read it).
//   Lost on page reload, which is fine: the refresh cookie gets a new one.
// - refresh token: in the HttpOnly cookie "amazfy_refresh"; JavaScript can't read it, the browser sends it to
//   /api/v1/auth/* by itself.

let accessToken: string | null = null

export function setAccessToken(token: string | null) {
  accessToken = token
}

export function hasAccessToken() {
  return accessToken !== null
}

/** RFC 7807 problem details, as the backend sends them for every error. */
export interface Problem {
  status?: number
  title?: string
  detail?: string
  errors?: { field: string; message: string }[]
}

/** An error answer from the backend: its HTTP status and problem details. */
export class ApiError extends Error {
  readonly status: number
  readonly problem: Problem
  readonly retryAfterSeconds: number | null

  constructor(status: number, problem: Problem, retryAfter: string | null) {
    super(problem.detail ?? `Request failed (${status})`)
    this.status = status
    this.problem = problem
    this.retryAfterSeconds = retryAfter ? Number(retryAfter) : null
  }
}

/** The message to show for a failed request: the backend's safe text, or a generic one for network trouble. */
export function errorMessage(error: unknown): string | undefined {
  if (!error) return undefined
  return error instanceof ApiError ? error.message : 'Something went wrong. Please try again.'
}

/** One refresh at a time: requests failing together all wait for the same refresh instead of each starting one. */
let refreshing: Promise<boolean> | null = null

/**
 * Exchanges the refresh cookie for a new access token. True if the user is (still) logged in.
 * Each refresh token works once (rotation), which is why parallel refreshes must be avoided.
 */
export function refreshAccessToken(): Promise<boolean> {
  refreshing ??= fetch('/api/v1/auth/refresh', { method: 'POST' })
    .then(async (res) => {
      setAccessToken(res.ok ? ((await res.json()) as { accessToken: string }).accessToken : null)
      return res.ok
    })
    .catch(() => false)
    .finally(() => {
      refreshing = null
    })
  return refreshing
}

/** The fetch function the generated client calls. Returns the shape it expects: { data, status, headers }. */
export async function apiFetch<T>(url: string, options: RequestInit = {}): Promise<T> {
  const send = () => {
    const headers = new Headers(options.headers)
    if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`)
    return fetch(url, { ...options, headers })
  }

  let res = await send()
  // Access tokens expire after 15 minutes: renew once and repeat the request. Not for the auth endpoints themselves
  // (a 401 from login means a wrong password, not an expired token).
  if (res.status === 401 && !url.startsWith('/api/v1/auth/') && (await refreshAccessToken())) {
    res = await send()
  }

  const body = [204, 205, 304].includes(res.status) ? null : await res.text()
  const data = body ? JSON.parse(body) : {}
  if (!res.ok) throw new ApiError(res.status, data as Problem, res.headers.get('Retry-After'))
  return { data, status: res.status, headers: res.headers } as T
}

export default apiFetch
