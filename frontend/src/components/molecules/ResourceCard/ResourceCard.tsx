import { Badge } from '@/components/atoms/Badge'
import { ProgressBar } from '@/components/atoms/ProgressBar'
import { getResourceStatus } from '@/utils/resourceStatus'
import styles from './ResourceCard.module.css'

export interface ResourceCardProps {
  label: string
  capacity: number
  holders: string[]
  waiting: string[]
}

/** Tarjeta de un recurso compartido: ocupación, quién lo usa y cuántas operaciones esperan. */
export function ResourceCard({ label, capacity, holders, waiting }: ResourceCardProps) {
  const status = getResourceStatus(holders.length, capacity)

  return (
    <article className={styles.card}>
      <header className={styles.header}>
        <h3 className={styles.title}>{label}</h3>
        <Badge tone={status.tone}>{status.label}</Badge>
      </header>

      <ProgressBar value={holders.length} max={capacity} label={`Ocupación de ${label}`} />
      <p className={styles.usage}>
        {holders.length} / {capacity} en uso
        {waiting.length > 0 && <span className={styles.waiting}> · {waiting.length} en espera</span>}
      </p>

      {holders.length > 0 && (
        <ul className={styles.holders} aria-label={`Operaciones usando ${label}`}>
          {holders.map((id) => (
            <li key={id} className={styles.holder}>{id}</li>
          ))}
        </ul>
      )}
    </article>
  )
}
