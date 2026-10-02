import type { SelectHTMLAttributes } from 'react'
import styles from './Select.module.css'

export interface SelectOption {
  value: string
  label: string
}

export interface SelectProps extends Omit<SelectHTMLAttributes<HTMLSelectElement>, 'className' | 'children'> {
  options: readonly SelectOption[]
  /** Texto de la opción vacía inicial; si se omite no hay opción vacía. */
  placeholder?: string
}

export function Select({ options, placeholder, ...rest }: SelectProps) {
  return (
    <select className={styles.select} {...rest}>
      {placeholder !== undefined && (
        <option value="" disabled>
          {placeholder}
        </option>
      )}
      {options.map((option) => (
        <option key={option.value} value={option.value}>
          {option.label}
        </option>
      ))}
    </select>
  )
}
