import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const sentimentApiMocks = vi.hoisted(() => ({
  getSentimentLatest: vi.fn(),
  getSentimentHistory: vi.fn(),
  getLatestNews: vi.fn(),
}))

const watchlistApiMocks = vi.hoisted(() => ({ getWatchlist: vi.fn() }))
const analysisApiMocks = vi.hoisted(() => ({ runFullAnalysis: vi.fn() }))

vi.mock('../api/sentiment', () => sentimentApiMocks)
vi.mock('../api/watchlist', () => watchlistApiMocks)
vi.mock('../api/analysis', () => analysisApiMocks)

interface Deferred<T> {
  promise: Promise<T>
  resolve: (value: T) => void
}

function deferred<T>(): Deferred<T> {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((resolvePromise) => {
    resolve = resolvePromise
  })
  return { promise, resolve }
}

function legacySuccess<T>(data: T) {
  return { success: true as const, data }
}

function sentiment(symbol: string, summary: string) {
  return {
    id: symbol === 'TCS' ? 2 : 1,
    symbol,
    date: '2026-08-26',
    score: 'POSITIVE' as const,
    summary,
    rawContent: summary,
    confidence: 0.82,
    analyzedAt: '2026-08-26T08:00:00Z',
    redFlags: [],
    catalysts: ['Strong demand'],
  }
}

async function mountSentiment() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/sentiment', name: 'sentiment', component: { template: '<div />' } }],
  })
  await router.push('/sentiment')
  await router.isReady()

  const SentimentView = (await import('./SentimentView.vue')).default
  return mount(SentimentView, {
    global: {
      plugins: [router],
      stubs: {
        SentimentBadge: {
          props: ['score', 'confidence'],
          template: '<div>{{ score }} {{ confidence }}</div>',
        },
        SentimentTimeline: true,
        ArticleBrowser: true,
        AnalysisAccordion: true,
      },
    },
  })
}

describe('SentimentView — latest symbol request wins', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    watchlistApiMocks.getWatchlist.mockResolvedValue([])
  })

  it('ignores an older quick-analysis snapshot that resolves after the newer symbol', async () => {
    const relianceSentiment =
      deferred<ReturnType<typeof legacySuccess<ReturnType<typeof sentiment>>>>()
    const relianceNews = deferred<ReturnType<typeof legacySuccess<unknown[]>>>()
    const tcsSentiment = deferred<ReturnType<typeof legacySuccess<ReturnType<typeof sentiment>>>>()
    const tcsNews = deferred<ReturnType<typeof legacySuccess<unknown[]>>>()

    sentimentApiMocks.getSentimentLatest
      .mockReturnValueOnce(relianceSentiment.promise)
      .mockReturnValueOnce(tcsSentiment.promise)
    sentimentApiMocks.getLatestNews
      .mockReturnValueOnce(relianceNews.promise)
      .mockReturnValueOnce(tcsNews.promise)

    const wrapper = await mountSentiment()
    await flushPromises()

    const input = wrapper.get('input[list="watchlistSymbols"]')
    const viewSentiment = wrapper
      .findAll('button')
      .find((button) => button.text().trim() === 'View Sentiment')
    expect(viewSentiment).toBeDefined()

    await input.setValue('RELIANCE')
    void viewSentiment!.trigger('click')

    ;(input.element as HTMLInputElement).value = 'TCS'
    void input.trigger('input')
    void viewSentiment!.trigger('click')

    tcsSentiment.resolve(legacySuccess(sentiment('TCS', 'TCS is the newest sentiment snapshot')))
    tcsNews.resolve(legacySuccess([]))
    await flushPromises()

    const historyToggle = wrapper
      .findAll('button')
      .find((button) => button.text().trim() === 'Historical Sentiment')
    expect(historyToggle).toBeDefined()
    await historyToggle!.trigger('click')
    expect(wrapper.text()).toContain('TCS is the newest sentiment snapshot')

    relianceSentiment.resolve(legacySuccess(sentiment('RELIANCE', 'RELIANCE is stale')))
    relianceNews.resolve(legacySuccess([]))
    await flushPromises()

    expect(wrapper.text()).toContain('TCS is the newest sentiment snapshot')
    expect(wrapper.text()).not.toContain('RELIANCE is stale')
    expect(sentimentApiMocks.getSentimentLatest).toHaveBeenNthCalledWith(1, 'RELIANCE')
    expect(sentimentApiMocks.getSentimentLatest).toHaveBeenNthCalledWith(2, 'TCS')

    wrapper.unmount()
  })
})
