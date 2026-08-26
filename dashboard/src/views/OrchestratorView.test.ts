import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { JobRunProgressResponse, JobRunResponse } from '../api/types'
import OrchestratorView from './OrchestratorView.vue'

const apiMocks = vi.hoisted(() => ({
  startJobRun: vi.fn(),
  getJobRunProgress: vi.fn(),
  listJobRuns: vi.fn(),
  cancelJobRun: vi.fn(),
}))

vi.mock('../api/job', () => apiMocks)

const activeRun: JobRunResponse = {
  runId: 'run-123',
  triggerType: 'MANUAL',
  status: 'RUNNING',
  startedAt: '2026-08-25T10:00:00',
  completedAt: null,
  symbolsCount: 2,
  completedCount: 0,
  failedCount: 0,
  errorMessage: null,
}

const runningProgress: JobRunProgressResponse = {
  runId: activeRun.runId,
  status: 'RUNNING',
  totalSymbols: 2,
  completedSymbols: 0,
  failedSymbols: 0,
  stages: [],
}

function mountView() {
  return mount(OrchestratorView, {
    global: {
      stubs: {
        ErrorBoundary: {
          props: ['error'],
          template: '<div><slot /><slot v-if="error" name="error" /></div>',
        },
        LoadingSpinner: { template: '<div>Loading orchestrator data...</div>' },
        StageIcon: true,
        StatusBadge: true,
      },
    },
  })
}

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((resolver) => {
    resolve = resolver
  })
  return { promise, resolve }
}

beforeEach(() => {
  vi.clearAllMocks()
  apiMocks.listJobRuns.mockResolvedValue({ success: true, data: [] })
  apiMocks.getJobRunProgress.mockResolvedValue({ success: true, data: runningProgress })
  apiMocks.cancelJobRun.mockResolvedValue({ success: true })
})

afterEach(() => {
  vi.useRealTimers()
})

describe('OrchestratorView — Run control', () => {
  it('shows an immediate starting state and prevents duplicate start requests', async () => {
    const startRequest = deferred<{ success: true; data: JobRunResponse }>()
    apiMocks.startJobRun.mockReturnValue(startRequest.promise)
    const wrapper = mountView()
    await flushPromises()

    const runButton = wrapper.find('[aria-label="Run job orchestrator"]')
    await runButton.trigger('click')

    expect(apiMocks.startJobRun).toHaveBeenCalledTimes(1)
    expect(runButton.attributes('disabled')).toBeDefined()
    expect(runButton.text()).toContain('Starting...')

    await runButton.trigger('click')
    expect(apiMocks.startJobRun).toHaveBeenCalledTimes(1)

    startRequest.resolve({ success: true, data: activeRun })
    await flushPromises()

    expect(runButton.attributes('disabled')).toBeDefined()
    expect(runButton.text()).toContain('Running...')
    expect(wrapper.text()).toContain('Pipeline run started')
    wrapper.unmount()
  })

  it('resumes polling for a restored running job and re-enables Run when it completes', async () => {
    vi.useFakeTimers()
    apiMocks.listJobRuns.mockResolvedValue({ success: true, data: [activeRun] })
    apiMocks.getJobRunProgress
      .mockResolvedValueOnce({ success: true, data: runningProgress })
      .mockResolvedValueOnce({
        success: true,
        data: {
          ...runningProgress,
          status: 'COMPLETED',
          completedSymbols: 2,
          completedAt: '2026-08-25T10:05:00',
        },
      })

    const wrapper = mountView()
    await flushPromises()

    const runButton = wrapper.find('[aria-label="Run job orchestrator"]')
    expect(runButton.text()).toContain('Running...')
    expect(runButton.attributes('disabled')).toBeDefined()

    await vi.advanceTimersByTimeAsync(3000)
    await flushPromises()

    expect(apiMocks.getJobRunProgress).toHaveBeenCalledTimes(2)
    expect(runButton.text()).toBe('Run')
    expect(runButton.attributes('disabled')).toBeUndefined()
    wrapper.unmount()
  })

  it('restores the Run button and surfaces the API error when starting fails', async () => {
    apiMocks.startJobRun.mockResolvedValue({ success: false, error: 'Backend rejected the run' })
    const wrapper = mountView()
    await flushPromises()

    const runButton = wrapper.find('[aria-label="Run job orchestrator"]')
    await runButton.trigger('click')
    await flushPromises()

    expect(runButton.text()).toBe('Run')
    expect(runButton.attributes('disabled')).toBeUndefined()
    expect(wrapper.text()).toContain('Backend rejected the run')
    wrapper.unmount()
  })
})
