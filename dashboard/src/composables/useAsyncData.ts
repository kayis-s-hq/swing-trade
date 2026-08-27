import { onScopeDispose, ref, shallowRef } from 'vue'
import { AppError, asAppError } from '../errors/appError'

type AsyncLoader<T> = (signal: AbortSignal) => Promise<T>

function isAbortError(error: unknown): boolean {
  return (
    typeof error === 'object' && error !== null && 'name' in error && error.name === 'AbortError'
  )
}

export function useAsyncData<T = unknown>() {
  const data = shallowRef<T | null>(null)
  const loading = ref(false)
  const error = shallowRef<AppError | null>(null)
  const errorMessage = ref('')
  const isInitialLoading = ref(false)
  const isRefreshing = ref(false)
  const isStale = ref(false)
  const lastSuccessfulAt = ref<number | null>(null)

  let activeController: AbortController | null = null
  let latestRequestId = 0
  let lastLoader: AsyncLoader<T> | null = null

  function finishLoading(): void {
    loading.value = false
    isInitialLoading.value = false
    isRefreshing.value = false
  }

  function cancel(): void {
    if (!activeController) return

    latestRequestId += 1
    activeController.abort()
    activeController = null
    finishLoading()
  }

  async function execute(loader: AsyncLoader<T>): Promise<void> {
    lastLoader = loader
    activeController?.abort()

    const controller = new AbortController()
    activeController = controller
    const requestId = ++latestRequestId
    const hasUsableData = data.value !== null

    loading.value = true
    isInitialLoading.value = !hasUsableData
    isRefreshing.value = hasUsableData

    try {
      const result = await loader(controller.signal)
      if (requestId !== latestRequestId) return

      data.value = result
      error.value = null
      errorMessage.value = ''
      isStale.value = false
      lastSuccessfulAt.value = Date.now()
    } catch (errorLike: unknown) {
      if (requestId !== latestRequestId || controller.signal.aborted || isAbortError(errorLike)) {
        return
      }

      const requestError = asAppError(errorLike)
      error.value = requestError
      errorMessage.value = requestError.message
      isStale.value = data.value !== null
    } finally {
      if (requestId === latestRequestId) {
        activeController = null
        finishLoading()
      }
    }
  }

  function retry(): Promise<void> {
    return lastLoader ? execute(lastLoader) : Promise.resolve()
  }

  onScopeDispose(cancel, true)

  return {
    data,
    loading,
    error,
    errorMessage,
    isInitialLoading,
    isRefreshing,
    isStale,
    lastSuccessfulAt,
    execute,
    retry,
    cancel,
  }
}
