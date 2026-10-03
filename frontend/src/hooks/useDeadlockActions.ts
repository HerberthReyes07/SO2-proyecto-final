import { useRemoteAction } from '@/hooks/useRemoteAction'
import { changePrevention, resolveDeadlock, startScenario } from '@/services/deadlockApi'
import type { PreventionModeId } from '@/types/catalog'

interface DeadlockActions {
  changePrevention: (mode: PreventionModeId) => void
  startScenario: (mode: PreventionModeId) => void
  resolve: (continueOperationId: string) => void
  /** Hay alguna de las tres acciones en curso. */
  pending: boolean
  /** Error de la última acción que falló, si la hubo. */
  error: string | null
  /** Mensaje de la última resolución exitosa. */
  resolution: string | null
}

/** Las acciones del panel de deadlock con su estado agregado (en curso, error, resultado). */
export function useDeadlockActions(): DeadlockActions {
  const prevention = useRemoteAction(changePrevention)
  const scenario = useRemoteAction(startScenario)
  const resolution = useRemoteAction(resolveDeadlock)

  const result = resolution.result
  return {
    changePrevention: (mode) => void prevention.run(mode),
    startScenario: (mode) => void scenario.run(mode),
    resolve: (continueOperationId) => void resolution.run(continueOperationId),
    pending: prevention.pending || scenario.pending || resolution.pending,
    error: resolution.error ?? scenario.error ?? prevention.error,
    resolution: result && `${result.continued} continuó; ${result.returnedToWaiting} volvió a la lista de espera.`,
  }
}
