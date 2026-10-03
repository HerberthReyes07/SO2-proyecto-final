import { Badge } from '@/components/atoms/Badge'
import { ProgressBar } from '@/components/atoms/ProgressBar'
import { RESOURCE_LABELS } from '@/constants/resourceLabels'
import { STAGE_LABELS } from '@/constants/stageLabels'
import type { OrderProgress } from '@/types/simulation'
import { formatSimulatedDuration } from '@/utils/formatSimulatedDuration'
import styles from './ActiveOrderItem.module.css'

export interface ActiveOrderItemProps {
  order: OrderProgress
  /** Nombre del nivel de servicio del pedido (ej. "Exprés"). */
  serviceLevelLabel: string
}

const PERCENT = 100

/** Un pedido en proceso: quién es, en qué etapa va, qué retiene o espera y cuánto le falta. */
export function ActiveOrderItem({ order, serviceLevelLabel }: ActiveOrderItemProps) {
  const percent = Math.round(order.progress * PERCENT)

  return (
    <li className={styles.item}>
      <header className={styles.header}>
        <span className={styles.id}>{order.orderId}</span>
        <span className={styles.customer}>{order.customerName}</span>
        <Badge>{serviceLevelLabel}</Badge>
        {order.retries > 0 && <Badge tone="warning">Reintento {order.retries}</Badge>}
      </header>

      <p className={styles.meta}>
        {order.totalItems} artículos · {STAGE_LABELS[order.stage]}
      </p>

      <ProgressBar value={percent} max={PERCENT} label={`Avance de ${order.orderId}`} />
      <p className={styles.meta}>
        {percent}% · restante ≈ {formatSimulatedDuration(order.remainingMillis)} simulados
      </p>

      <div className={styles.resources}>
        {order.awaiting && <Badge tone="warning">Esperando: {RESOURCE_LABELS[order.awaiting]}</Badge>}
        {order.held.map((resource) => (
          <Badge key={resource} tone="success">
            {RESOURCE_LABELS[resource]}
          </Badge>
        ))}
      </div>
    </li>
  )
}
