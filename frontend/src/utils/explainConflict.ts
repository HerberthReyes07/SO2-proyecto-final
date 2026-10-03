import { RESOURCE_LABELS } from '@/constants/resourceLabels'
import type { DeadlockReport } from '@/types/simulation'

/**
 * Explica en frases por qué las dos operaciones en conflicto no pueden avanzar. Si se esperan
 * mutuamente es la forma clásica (A espera lo de B y B lo de A); si no, el ciclo pasa por una tercera.
 */
export function explainConflict(report: DeadlockReport): string[] {
  const waiter = report.operations.find((operation) => operation.id === report.conflict.waiter)
  const holder = report.operations.find((operation) => operation.id === report.conflict.holder)
  if (!waiter || !holder) return []

  const first = `${waiter.id} espera ${RESOURCE_LABELS[waiter.waitingFor]}, que retiene ${holder.id}.`
  const holderWaitsForWaiter = waiter.held.includes(holder.waitingFor)
  const second = holderWaitsForWaiter
    ? `${holder.id} espera ${RESOURCE_LABELS[holder.waitingFor]}, que retiene ${waiter.id}.`
    : `${holder.id} a su vez espera ${RESOURCE_LABELS[holder.waitingFor]}, y así se cierra un ciclo de espera.`
  const conclusion = holderWaitsForWaiter
    ? 'Se esperan mutuamente: ninguna puede avanzar mientras la otra siga reteniendo lo que necesita.'
    : 'Ninguna puede avanzar: cada una espera algo que retiene otra del ciclo.'

  return [first, second, conclusion]
}
