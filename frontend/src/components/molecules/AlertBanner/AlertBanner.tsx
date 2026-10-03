import { Button } from '@/components/atoms/Button'
import styles from './AlertBanner.module.css'

export interface AlertBannerProps {
  message: string
  /** Texto del botón de acción; si se omite no se muestra botón. */
  actionLabel?: string
  onAction?: () => void
}

/** Aviso destacado que se ve desde cualquier pestaña (se anuncia a lectores de pantalla como alerta). */
export function AlertBanner({ message, actionLabel, onAction }: AlertBannerProps) {
  return (
    <div className={styles.banner} role="alert">
      <span className={styles.message}>{message}</span>
      {actionLabel && onAction && (
        <Button variant="primary" onClick={onAction}>
          {actionLabel}
        </Button>
      )}
    </div>
  )
}
