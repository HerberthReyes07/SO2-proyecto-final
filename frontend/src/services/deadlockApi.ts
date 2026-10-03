import { sendJson } from '@/services/apiClient'
import type { PreventionModeId } from '@/types/catalog'

/** Resultado de resolver un deadlock: quién continuó y quién volvió a la lista de espera. */
export interface Resolution {
  continued: string
  returnedToWaiting: string
}

/** Cambia la estrategia de prevención de deadlock; el servidor devuelve la que quedó vigente. */
export function changePrevention(mode: PreventionModeId): Promise<{ prevention: PreventionModeId }> {
  return sendJson<{ prevention: PreventionModeId }>('/api/deadlock/prevention', 'PUT', { mode })
}

/** Resuelve el deadlock vigente dejando continuar a una de las dos operaciones en conflicto. */
export function resolveDeadlock(continueOperationId: string): Promise<Resolution> {
  return sendJson<Resolution>('/api/deadlock/resolve', 'POST', { continueOperationId })
}

/** Lanza el escenario de demostración con la prevención indicada; sin prevención provoca el deadlock. */
export function startScenario(mode: PreventionModeId): Promise<{ prevention: PreventionModeId }> {
  return sendJson<{ prevention: PreventionModeId }>('/api/deadlock/scenario', 'POST', { mode })
}
