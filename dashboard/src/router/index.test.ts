import { describe, expect, it, vi } from 'vitest'

const routerMocks = vi.hoisted(() => {
  const router = { onError: vi.fn() }
  return {
    router,
    history: { name: 'history' },
    createRouter: vi.fn(() => router),
    createWebHistory: vi.fn(),
    reportRuntimeError: vi.fn(),
  }
})

routerMocks.createWebHistory.mockReturnValue(routerMocks.history)

vi.mock('vue-router', () => ({
  createRouter: routerMocks.createRouter,
  createWebHistory: routerMocks.createWebHistory,
}))
vi.mock('../views/DashboardView.vue', () => ({ default: { name: 'DashboardView' } }))
vi.mock('../stores/runtimeErrors', () => ({
  reportRuntimeError: routerMocks.reportRuntimeError,
}))

describe('router runtime errors', () => {
  it('reports navigation and lazy-route failures through router.onError', async () => {
    vi.clearAllMocks()
    await import('./index')

    expect(routerMocks.router.onError).toHaveBeenCalledTimes(1)
    const onError = routerMocks.router.onError.mock.calls[0][0]
    const error = new Error('Failed to fetch dynamically imported module')

    onError(error, { fullPath: '/signals' }, { fullPath: '/' })

    expect(routerMocks.reportRuntimeError).toHaveBeenCalledTimes(1)
    expect(routerMocks.reportRuntimeError).toHaveBeenCalledWith(error, {
      source: 'router',
      route: '/signals',
    })
  })
})
