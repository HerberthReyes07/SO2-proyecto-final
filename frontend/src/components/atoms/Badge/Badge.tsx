import type { ReactNode } from 'react'
import styles from './Badge.module.css'

export type BadgeTone = 'success' | 'warning' | 'danger' | 'neutral'

export interface BadgeProps {
  tone?: BadgeTone
  children: ReactNode
}

/** Etiqueta pequeña de color para comunicar un estado. */
export function Badge({ tone = 'neutral', children }: BadgeProps) {
  return <span className={`${styles.badge} ${styles[tone]}`}>{children}</span>
}
