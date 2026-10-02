import styles from './ProgressBar.module.css'

export interface ProgressBarProps {
  value: number
  max: number
  /** Texto para lectores de pantalla, ej. "Ocupación de Montacargas". */
  label: string
}

/** Barra de progreso accesible. Si `max` es 0 se muestra vacía en lugar de dividir entre cero. */
export function ProgressBar({ value, max, label }: ProgressBarProps) {
  const ratio = max > 0 ? Math.min(Math.max(value / max, 0), 1) : 0

  return (
    <div
      className={styles.track}
      role="progressbar"
      aria-label={label}
      aria-valuemin={0}
      aria-valuemax={max}
      aria-valuenow={value}
    >
      <div className={styles.fill} style={{ width: `${ratio * 100}%` }} />
    </div>
  )
}
