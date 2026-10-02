import { Badge } from '@/components/atoms/Badge'
import styles from './WaitingOrderItem.module.css'

export interface WaitingOrderItemProps {
  /** Posición en la lista (desde 1): es el orden en que se atenderá. */
  position: number
  orderId: string
  customerName: string
  serviceLevelLabel: string
  totalItems: number
  /** Dato que la política vigente usa para ordenar este pedido (ej. "Vence en 3 min 10 s"). */
  criterion: string
  overdue: boolean
}

/** Una fila de la lista de espera. */
export function WaitingOrderItem({
  position,
  orderId,
  customerName,
  serviceLevelLabel,
  totalItems,
  criterion,
  overdue,
}: WaitingOrderItemProps) {
  return (
    <li className={styles.item}>
      <span className={styles.position} aria-label={`Posición ${position}`}>
        {position}
      </span>
      <div className={styles.body}>
        <div className={styles.header}>
          <span className={styles.id}>{orderId}</span>
          <span className={styles.customer}>{customerName}</span>
          <Badge>{serviceLevelLabel}</Badge>
          {overdue && <Badge tone="danger">Retrasado</Badge>}
        </div>
        <p className={styles.meta}>
          {totalItems} artículos · <strong className={styles.criterion}>{criterion}</strong>
        </p>
      </div>
    </li>
  )
}
