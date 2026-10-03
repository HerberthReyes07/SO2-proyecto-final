import { Badge } from '@/components/atoms/Badge'
import { ProgressBar } from '@/components/atoms/ProgressBar'
import { RESOURCE_LABELS } from '@/constants/resourceLabels'
import { TRUCK_PHASE_LABELS } from '@/constants/truckPhaseLabels'
import type { Truck } from '@/types/simulation'
import styles from './TruckItem.module.css'

export interface TruckItemProps {
  truck: Truck
  productName: string
}

const PERCENT = 100

/** Un camión en Recepción: qué trae, en qué fase va y qué recursos retiene o espera. */
export function TruckItem({ truck, productName }: TruckItemProps) {
  const percent = Math.round(truck.progress * PERCENT)

  return (
    <li className={styles.item}>
      <header className={styles.header}>
        <span className={styles.id}>{truck.truckId}</span>
        <span className={styles.product}>
          {productName} × {truck.quantity}
        </span>
        <Badge tone={truck.phase === 'REPORTING' ? 'warning' : 'neutral'}>{TRUCK_PHASE_LABELS[truck.phase]}</Badge>
      </header>
      <ProgressBar value={percent} max={PERCENT} label={`Descarga de ${truck.truckId}`} />
      <div className={styles.resources}>
        {truck.awaiting && <Badge tone="warning">Esperando: {RESOURCE_LABELS[truck.awaiting]}</Badge>}
        {truck.held.map((resource) => (
          <Badge key={resource} tone="success">
            {RESOURCE_LABELS[resource]}
          </Badge>
        ))}
      </div>
    </li>
  )
}
