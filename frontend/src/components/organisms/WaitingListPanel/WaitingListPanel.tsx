import { Badge } from '@/components/atoms/Badge'
import { PolicySelector } from '@/components/molecules/PolicySelector'
import { WaitingOrderItem } from '@/components/molecules/WaitingOrderItem'
import type { PolicyId, PolicyInfo } from '@/types/catalog'
import type { WaitingOrder } from '@/types/simulation'
import { describeCriterion, isOverdue } from '@/utils/policyCriterion'
import styles from './WaitingListPanel.module.css'

export interface WaitingListPanelProps {
  policies: readonly PolicyInfo[]
  currentPolicy: PolicyId
  /** Los primeros pedidos en espera, ya en el orden en que se atenderán. */
  waiting: WaitingOrder[]
  /** Total de pedidos en espera; puede superar los que se muestran. */
  waitingTotal: number
  /** Diccionario id de nivel de servicio → nombre mostrado. */
  serviceLevelNames: Record<string, string>
  onPolicyChange: (policy: PolicyId) => void
  policyError?: string | null
}

/** Lista de espera con la política de atención vigente y el criterio de orden de cada pedido. */
export function WaitingListPanel({
  policies,
  currentPolicy,
  waiting,
  waitingTotal,
  serviceLevelNames,
  onPolicyChange,
  policyError,
}: WaitingListPanelProps) {
  const hidden = waitingTotal - waiting.length

  return (
    <section aria-labelledby="waiting-list-title">
      <header className={styles.header}>
        <h2 id="waiting-list-title" className={styles.title}>
          Lista de espera
        </h2>
        <Badge tone={waitingTotal > 0 ? 'warning' : 'neutral'}>{waitingTotal} en espera</Badge>
      </header>

      <PolicySelector policies={policies} value={currentPolicy} onChange={onPolicyChange} error={policyError} />

      {waiting.length === 0 ? (
        <p className={styles.empty}>No hay pedidos esperando.</p>
      ) : (
        <>
          <p className={styles.caption}>Orden de atención, del primero al último:</p>
          <ol className={styles.list}>
            {waiting.map((order, index) => {
              const levelLabel = serviceLevelNames[order.serviceLevel] ?? order.serviceLevel
              return (
                <WaitingOrderItem
                  key={order.orderId}
                  position={index + 1}
                  orderId={order.orderId}
                  customerName={order.customerName}
                  serviceLevelLabel={levelLabel}
                  totalItems={order.totalItems}
                  criterion={describeCriterion(currentPolicy, order, levelLabel)}
                  overdue={isOverdue(order)}
                />
              )
            })}
          </ol>
          {hidden > 0 && <p className={styles.more}>… y {hidden} pedidos más en espera</p>}
        </>
      )}
    </section>
  )
}
