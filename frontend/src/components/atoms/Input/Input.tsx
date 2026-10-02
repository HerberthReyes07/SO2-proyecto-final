import type { InputHTMLAttributes } from 'react'
import styles from './Input.module.css'

export interface InputProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'className'> {
  invalid?: boolean
}

export function Input({ invalid, ...rest }: InputProps) {
  return <input className={styles.input} aria-invalid={invalid || undefined} {...rest} />
}
