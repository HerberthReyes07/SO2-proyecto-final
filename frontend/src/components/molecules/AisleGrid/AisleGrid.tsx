import { LocationCell } from '@/components/atoms/LocationCell'
import styles from './AisleGrid.module.css'

/** Una ubicación ya lista para dibujarse. */
export interface AisleCell {
  /** Índice lineal de la ubicación; sirve de clave estable. */
  index: number
  label: string
  fillRatio: number
  color?: string
  highlighted: boolean
}

export interface AisleGridProps {
  title: string
  positions: number
  levels: number
  /** Las ubicaciones del pasillo en orden de índice: por posición y, dentro de cada una, de menor a mayor nivel. */
  cells: readonly AisleCell[]
}

/**
 * Un pasillo visto de frente: una columna por posición y una fila por nivel, con el nivel más alto
 * arriba (como una estantería real). Las ubicaciones llegan ordenadas por índice, donde el nivel es lo
 * que cambia más rápido, así que cada columna se recorre de abajo hacia arriba.
 */
export function AisleGrid({ title, positions, levels, cells }: AisleGridProps) {
  // El CSS Grid llena por filas de arriba hacia abajo: se reordena para que la fila superior sea el último nivel.
  const rows = Array.from({ length: levels }, (_, row) => levels - 1 - row)
  const ordered = rows.flatMap((level) =>
    Array.from({ length: positions }, (_, position) => cells[position * levels + level]),
  )

  return (
    <figure className={styles.aisle}>
      <figcaption className={styles.title}>{title}</figcaption>
      <div className={styles.grid} style={{ gridTemplateColumns: `repeat(${positions}, 1fr)` }}>
        {ordered.map((cell) => (
          <LocationCell
            key={cell.index}
            label={cell.label}
            fillRatio={cell.fillRatio}
            color={cell.color}
            highlighted={cell.highlighted}
          />
        ))}
      </div>
    </figure>
  )
}
