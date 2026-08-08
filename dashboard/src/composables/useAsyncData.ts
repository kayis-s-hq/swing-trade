import { ref, Ref } from 'vue';

export function useAsyncData<T = unknown>() {
  const data = ref<T | null>(null);
  const loading = ref(true);
  const error = ref<Error | null>(null);
  const errorMessage = ref('');

  async function execute(fn: () => Promise<T>): Promise<void> {
    loading.value = true;
    error.value = null;
    errorMessage.value = '';
    try {
      data.value = await fn();
    } catch (err: unknown) {
      error.value = err as Error;
      errorMessage.value = err instanceof Error ? err.message : 'Unknown error';
    } finally {
      loading.value = false;
    }
  }

  return { data, loading, error, errorMessage, execute };
}
