import { sendJson } from '@/services/apiClient'
import type { BenchmarkState, StartBenchmarkRequest } from '@/types/benchmark'

/** Lanza una comparación de políticas con la carga indicada. El servidor responde de inmediato: corre en segundo plano. */
export function startBenchmark(request: StartBenchmarkRequest): Promise<BenchmarkState> {
  return sendJson<BenchmarkState>('/api/benchmark', 'POST', request)
}

/** Cancela la comparación en marcha; si no hay ninguna, no hace nada. */
export function cancelBenchmark(): Promise<void> {
  return sendJson<void>('/api/benchmark', 'DELETE', undefined)
}
