import type { PolicyId } from '@/types/catalog'
import type { StatsSnapshot } from '@/types/simulation'

/** Tipos del comparador de políticas (record `BenchmarkSnapshot` del backend). */

export type BenchmarkPhase = 'IDLE' | 'RUNNING' | 'DONE' | 'CANCELLED' | 'FAILED'

/** La carga con que se compara. */
export interface BenchmarkParams {
  orders: number
  ordersPerMinute: number
  seed: number
}

/** Resultado de correr una política con la carga de prueba. */
export interface PolicyResult {
  policy: PolicyId
  stats: StatsSnapshot
  /** Tiempo simulado que tardó en despachar toda la carga, en ms. */
  durationMillis: number
  /** Ocupación promedio de los encargados de bodega, entre 0 y 1. */
  workerUtilization: number
}

export interface BenchmarkState {
  phase: BenchmarkPhase
  /** Carga de la comparación actual o última; null si nunca se lanzó una. */
  params: BenchmarkParams | null
  /** Política que se está corriendo; null si no hay una en marcha. */
  currentPolicy: PolicyId | null
  policiesDone: number
  policiesTotal: number
  /** Pedidos ya despachados por la política en marcha. */
  ordersCompleted: number
  /** Resultados de las políticas terminadas, en el orden en que corrieron. */
  results: PolicyResult[]
  /** Motivo del fallo cuando la fase es FAILED. */
  error: string | null
}

/** Cuerpo para lanzar una comparación; lo que falte lo completa el servidor. */
export interface StartBenchmarkRequest {
  orders?: number
  ordersPerMinute?: number
  seed?: number
}
