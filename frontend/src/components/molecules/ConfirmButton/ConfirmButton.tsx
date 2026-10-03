import { useState } from 'react'
import { Button } from '@/components/atoms/Button'
import styles from './ConfirmButton.module.css'

export interface ConfirmButtonProps {
  label: string
  /** Pregunta que se muestra antes de ejecutar; debe decir qué se pierde. */
  question: string
  confirmLabel: string
  onConfirm: () => void
  disabled?: boolean
}

/** Botón para acciones destructivas: el primer clic pide confirmación en el mismo lugar, el segundo ejecuta. */
export function ConfirmButton({ label, question, confirmLabel, onConfirm, disabled }: ConfirmButtonProps) {
  const [asking, setAsking] = useState(false)

  if (!asking) {
    return (
      <Button disabled={disabled} onClick={() => setAsking(true)}>
        {label}
      </Button>
    )
  }

  const handleConfirm = () => {
    setAsking(false)
    onConfirm()
  }

  return (
    <div className={styles.confirm} role="group" aria-label={question}>
      <span className={styles.question}>{question}</span>
      <Button variant="primary" disabled={disabled} onClick={handleConfirm}>
        {confirmLabel}
      </Button>
      <Button onClick={() => setAsking(false)}>Cancelar</Button>
    </div>
  )
}
