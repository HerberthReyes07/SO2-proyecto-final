import { useRemoteAction } from '@/hooks/useRemoteAction'
import { cancelBenchmark, startBenchmark } from '@/services/benchmarkApi'
import type { StartBenchmarkRequest } from '@/types/benchmark'

interface BenchmarkActions {
  start: (request: StartBenchmarkRequest) => void
  cancel: () => void
  /** Hay alguna de las dos acciones en curso. */
  pending: boolean
  /** Error de la última acción que falló, si la hubo. */
  error: string | null
}

/** Las acciones del comparador con su estado agregado (en curso, error). */
export function useBenchmarkActions(): BenchmarkActions {
  const start = useRemoteAction(startBenchmark)
  const cancel = useRemoteAction(cancelBenchmark)

  return {
    start: (request) => void start.run(request),
    cancel: () => void cancel.run(),
    pending: start.pending || cancel.pending,
    error: start.error ?? cancel.error,
  }
}
