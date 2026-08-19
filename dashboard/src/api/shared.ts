import { API_BASE_URL, DEFAULT_HEADERS, REQUEST_TIMEOUT } from './config'

export interface RawFetchResult {
  ok: boolean
  data: unknown
  error?: string
}

export async function rawFetch(path: string, init?: RequestInit, retries = 3): Promise<RawFetchResult> {
  let lastError: string = ''

  for (let attempt = 0; attempt <= retries; attempt++) {
    const controller = new AbortController()
    const timeoutId = setTimeout(() => controller.abort(), REQUEST_TIMEOUT)

    try {
      const response = await fetch(`${API_BASE_URL}${path}`, {
        ...init,
        headers: { ...DEFAULT_HEADERS, ...init?.headers },
        signal: controller.signal,
      })

      let data: unknown = null
      const contentType = response.headers.get('content-type') || ''

      if (contentType.includes('application/json')) {
        try {
          data = await response.json()
        } catch {
          data = null
        }
      } else {
        const text = await response.text().catch(() => '')
        data = { message: text.slice(0, 200) || `Server responded with status ${response.status}` }
      }

      if (!response.ok) {
        const msg = (data as any)?.message
        lastError =
          msg && msg !== 'Internal Server Error'
            ? msg
            : `Server responded with status ${response.status}`

        // Retry on 5xx or server errors
        if (response.status >= 500 && attempt < retries) {
          const delay = 1000 * Math.pow(2, attempt)
          await new Promise((r) => setTimeout(r, delay))
          continue
        }
        return { ok: false, data: null, error: lastError }
      }

      return { ok: true, data }
    } catch (err: unknown) {
      if (err instanceof DOMException && err.name === 'AbortError') {
        lastError = `Request timeout (${REQUEST_TIMEOUT}ms)`
      } else if (err instanceof TypeError && err.message.includes('fetch')) {
        lastError = 'Unable to connect to the backend server. Is it running?'
      } else {
        lastError = err instanceof Error ? err.message : 'Network error'
      }

      // Retry on network errors
      if (attempt < retries) {
        const delay = 1000 * Math.pow(2, attempt)
        await new Promise((r) => setTimeout(r, delay))
        continue
      }
    } finally {
      clearTimeout(timeoutId)
    }
  }

  return { ok: false, data: null, error: lastError }
}

export const errResponse = <T>(error: string): import('./types').ApiResponse<T> => ({ success: false, error })

export const unwrap = <T>(raw: RawFetchResult): T => {
  const resp = raw.data as import('./types').ApiResponse<T> | null
  return resp?.data as T
}

export const toNum = (v: number | string | null | undefined): number => {
  if (v === null || v === undefined) return 0
  return typeof v === 'string' ? parseFloat(v) : v
}