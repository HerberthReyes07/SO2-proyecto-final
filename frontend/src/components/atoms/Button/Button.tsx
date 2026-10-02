import type { ButtonHTMLAttributes } from 'react'
import styles from './Button.module.css'

export type ButtonVariant = 'primary' | 'secondary'

export interface ButtonProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'className'> {
  variant?: ButtonVariant
  /** Para botones que alternan un estado (ej. la velocidad elegida); se comunica con aria-pressed. */
  pressed?: boolean
}

export function Button({ variant = 'secondary', pressed, type = 'button', ...rest }: ButtonProps) {
  const className = [styles.button, styles[variant], pressed ? styles.pressed : ''].join(' ')
  return <button className={className} type={type} aria-pressed={pressed} {...rest} />
}
