import type { CSSProperties } from 'react'
import styles from './LocationCell.module.css'

export interface LocationCellProps {
  /** Descripción completa de la ubicación; es el texto del tooltip y lo que leen los lectores de pantalla. */
  label: string
  /** Fracción de la capacidad que está ocupada, entre 0 y 1. La ubicación libre es 0. */
  fillRatio: number
  /** Variable CSS del color del tipo de mercancía que guarda (ej. `var(--color-type-heavy)`); omitir si está libre. */
  color?: string
  /** Se resalta, p. ej. cuando el puntero está sobre el lote al que pertenece. */
  highlighted?: boolean
}

const PERCENT = 100

/** Una ubicación del almacén: vacía si está libre, y llena de abajo hacia arriba según lo que guarda. */
export function LocationCell({ label, fillRatio, color, highlighted }: LocationCellProps) {
  const fill = Math.min(Math.max(fillRatio, 0), 1)
  const style = { '--fill': `${fill * PERCENT}%`, '--cell-color': color } as CSSProperties
  const className = [styles.cell, color ? styles.occupied : styles.free, highlighted ? styles.highlighted : ''].join(' ')

  return <div className={className} style={style} role="img" aria-label={label} title={label} />
}
