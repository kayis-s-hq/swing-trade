import { describe, expect, it, vi } from 'vitest'

const mainMocks = vi.hoisted(() => {
  const app = {
    config: {} as { errorHandler?: (error: unknown, instance: unknown, info: string) => void },
    use: vi.fn(),
    mount: vi.fn(),
  }
  app.use.mockReturnValue(app)
  return {
    app,
    pinia: { name: 'pinia' },
    router: { name: 'router' },
    createApp: vi.fn(() => app),
    createPinia: vi.fn(),
    reportRuntimeError: vi.fn(),
    loadSettings: vi.fn().mockResolvedValue([]),
  }
})

mainMocks.createPinia.mockReturnValue(mainMocks.pinia)

vi.mock('vue', () => ({ createApp: mainMocks.createApp }))
vi.mock('pinia', () => ({ createPinia: mainMocks.createPinia }))
vi.mock('./App.vue', () => ({ default: { name: 'App' } }))
vi.mock('./router', () => ({ default: mainMocks.router }))
vi.mock('./stores/runtimeErrors', () => ({
  reportRuntimeError: mainMocks.reportRuntimeError,
}))
vi.mock('./stores/settings', () => ({
  loadSettings: mainMocks.loadSettings,
}))
vi.mock('./assets/main.css', () => ({}))

describe('main global Vue error handling', () => {
  it('registers uncaught Vue exceptions with the runtime error reporter', async () => {
    vi.clearAllMocks()
    await import('./main')

    expect(mainMocks.app.config.errorHandler).toEqual(expect.any(Function))
    const error = new Error('uncaught render failure')
    mainMocks.app.config.errorHandler!(error, null, 'render function')

    expect(mainMocks.reportRuntimeError).toHaveBeenCalledTimes(1)
    expect(mainMocks.reportRuntimeError).toHaveBeenCalledWith(error, {
      source: 'vue',
      info: 'render function',
    })
  })
})

describe('main settings bootstrap', () => {
  it('reports a failed initial settings load instead of swallowing it, and still mounts', async () => {
    vi.clearAllMocks()
    vi.resetModules()
    const loadError = new Error('settings service unreachable')
    mainMocks.loadSettings.mockRejectedValueOnce(loadError)

    await import('./main')
    // loadSettings().catch(...).finally(mount) resolves on a microtask tick.
    await new Promise((resolve) => setTimeout(resolve, 0))

    expect(mainMocks.reportRuntimeError).toHaveBeenCalledWith(loadError, {
      source: 'bootstrap',
      info: 'Initial settings load failed',
    })
    expect(mainMocks.app.mount).toHaveBeenCalledWith('#app')
  })
})
