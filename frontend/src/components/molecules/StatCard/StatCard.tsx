import styles from './StatCard.module.css'

export interface StatCardProps {
  label: string
  value: string
  /** Texto secundario opcional (ej. una aclaración de la unidad). */
  hint?: string
}

/** Un indicador: un valor grande con su etiqueta. */
export function StatCard({ label, value, hint }: StatCardProps) {
  return (
    <div className={styles.card}>
      <span className={styles.label}>{label}</span>
      <strong className={styles.value}>{value}</strong>
      {hint && <span className={styles.hint}>{hint}</span>}
    </div>
  )
}
