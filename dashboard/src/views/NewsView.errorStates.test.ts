import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const apiMocks = vi.hoisted(() => ({
  getLatestNews: vi.fn(),
  getWatchlist: vi.fn(),
}))

vi.mock('../api/client', () => apiMocks)
vi.mock('../api/sentiment', () => ({ getLatestNews: apiMocks.getLatestNews }))
vi.mock('../api/watchlist', () => ({ getWatchlist: apiMocks.getWatchlist }))

interface Deferred<T> {
  promise: Promise<T>
  resolve: (value: T) => void
  reject: (reason?: unknown) => void
}

function deferred<T>(): Deferred<T> {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

function legacySuccess<T>(data: T) {
  return { success: true as const, data }
}

function article(title: string, source: string) {
  return {
    title,
    link: `https://example.test/${title}`,
    description: `${title} description`,
    publishedDate: '2026-08-26T08:00:00Z',
    source,
    rawContent: `${title} full content`,
  }
}

async function submitSymbol(wrapper: ReturnType<typeof mount>, symbol: string) {
  await wrapper.get('input[list="watchlistSymbols"]').setValue(symbol)
  await wrapper.get('form').trigger('submit')
}

describe('NewsView — latest symbol request wins', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiMocks.getWatchlist.mockResolvedValue([])
  })

  it('commits only the newest symbol response when requests resolve out of order', async () => {
    const relianceRequest =
      deferred<ReturnType<typeof legacySuccess<ReturnType<typeof article>[]>>>()
    const tcsRequest = deferred<ReturnType<typeof legacySuccess<ReturnType<typeof article>[]>>>()
    apiMocks.getLatestNews
      .mockReturnValueOnce(relianceRequest.promise)
      .mockReturnValueOnce(tcsRequest.promise)

    const NewsView = (await import('./NewsView.vue')).default
    const wrapper = mount(NewsView)
    await flushPromises()

    await submitSymbol(wrapper, 'RELIANCE')
    await submitSymbol(wrapper, 'TCS')

    tcsRequest.resolve(legacySuccess([article('TCS wins newest request', 'Business Daily')]))
    await flushPromises()

    expect(wrapper.text()).toContain('Symbol:')
    expect(wrapper.text()).toContain('TCS')
    expect(wrapper.text()).toContain('TCS wins newest request')

    relianceRequest.resolve(legacySuccess([article('RELIANCE stale response', 'Market Wire')]))
    await flushPromises()

    expect(wrapper.text()).toContain('TCS wins newest request')
    expect(wrapper.text()).not.toContain('RELIANCE stale response')
    expect(apiMocks.getLatestNews).toHaveBeenNthCalledWith(1, 'RELIANCE')
    expect(apiMocks.getLatestNews).toHaveBeenNthCalledWith(2, 'TCS')

    wrapper.unmount()
  })
})
