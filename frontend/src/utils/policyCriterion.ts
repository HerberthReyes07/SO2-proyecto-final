import type { PolicyId } from '@/types/catalog'
import type { WaitingOrder } from '@/types/simulation'
import { formatSimulatedDuration } from '@/utils/formatSimulatedDuration'

/** Indica si el pedido ya esperó más que el tiempo objetivo de su nivel de servicio. */
export function isOverdue(order: WaitingOrder): boolean {
  return order.waitedMillis > order.targetWaitMillis
}

/**
 * Texto con el dato que la política vigente usa para ordenar este pedido. Mostrarlo junto a cada
 * fila hace evidente el criterio: quien mira la lista entiende por qué un pedido va antes que otro.
 */
export function describeCriterion(policy: PolicyId, order: WaitingOrder, serviceLevelLabel: string): string {
  const waited = formatSimulatedDuration(order.waitedMillis)

  switch (policy) {
    case 'FIFO':
      return `Llegó hace ${waited}`
    case 'STRICT_PRIORITY':
      return `Nivel ${serviceLevelLabel}`
    case 'AGING_PRIORITY':
      return `Nivel ${serviceLevelLabel} · esperó ${waited}`
    case 'EARLIEST_DEADLINE_FIRST': {
      const remaining = order.targetWaitMillis - order.waitedMillis
      return remaining >= 0
        ? `Vence en ${formatSimulatedDuration(remaining)}`
        : `Venció hace ${formatSimulatedDuration(-remaining)}`
    }
    case 'SHORTEST_JOB_FIRST':
      return `${order.totalItems} artículos`
  }
}
