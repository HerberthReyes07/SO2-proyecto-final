import { Badge } from '@/components/atoms/Badge'
import { ActiveOrderItem } from '@/components/molecules/ActiveOrderItem'
import type { OrderProgress } from '@/types/simulation'
import styles from './ActiveOrdersPanel.module.css'

export interface ActiveOrdersPanelProps {
  orders: OrderProgress[]
  completedCount: number
  /** Diccionario id de nivel de servicio → nombre mostrado. */
  serviceLevelNames: Record<string, string>
}

/** Pedidos que se están procesando ahora, con su avance, y el total ya despachado. */
export function ActiveOrdersPanel({ orders, completedCount, serviceLevelNames }: ActiveOrdersPanelProps) {
  return (
    <section aria-labelledby="active-orders-title">
      <header className={styles.header}>
        <h2 id="active-orders-title" className={styles.title}>
          Pedidos en proceso
        </h2>
        <Badge tone="success">Completados: {completedCount}</Badge>
      </header>

      {orders.length === 0 ? (
        <p className={styles.empty}>No hay pedidos en proceso. Registre uno desde el formulario.</p>
      ) : (
        <ul className={styles.list}>
          {orders.map((order) => (
            <ActiveOrderItem
              key={order.orderId}
              order={order}
              serviceLevelLabel={serviceLevelNames[order.serviceLevel] ?? order.serviceLevel}
            />
          ))}
        </ul>
      )}
    </section>
  )
}
