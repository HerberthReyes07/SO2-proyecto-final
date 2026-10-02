import { ResourceCard } from '@/components/molecules/ResourceCard'
import { RESOURCE_LABELS } from '@/constants/resourceLabels'
import type { PoolSnapshot } from '@/types/simulation'
import styles from './ResourcePanel.module.css'

export interface ResourcePanelProps {
  resources: PoolSnapshot[]
}

/** Panel con el estado de todos los recursos compartidos del centro de distribución. */
export function ResourcePanel({ resources }: ResourcePanelProps) {
  return (
    <section aria-labelledby="resource-panel-title">
      <h2 id="resource-panel-title" className={styles.title}>Recursos compartidos</h2>
      <div className={styles.grid}>
        {resources.map((pool) => (
          <ResourceCard
            key={pool.type}
            label={RESOURCE_LABELS[pool.type]}
            capacity={pool.capacity}
            holders={pool.holders}
            waiting={pool.waiting}
          />
        ))}
      </div>
    </section>
  )
}
