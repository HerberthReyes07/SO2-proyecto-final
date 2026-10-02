import type { ReactNode } from 'react'
import styles from './FormField.module.css'

export interface FormFieldProps {
  label: string
  /** Id del control hijo; asocia la etiqueta con él para lectores de pantalla. */
  htmlFor: string
  error?: string | null
  children: ReactNode
}

/** Etiqueta + control + mensaje de error opcional. */
export function FormField({ label, htmlFor, error, children }: FormFieldProps) {
  return (
    <div className={styles.field}>
      <label className={styles.label} htmlFor={htmlFor}>
        {label}
      </label>
      {children}
      {error && (
        <p className={styles.error} role="alert">
          {error}
        </p>
      )}
    </div>
  )
}
