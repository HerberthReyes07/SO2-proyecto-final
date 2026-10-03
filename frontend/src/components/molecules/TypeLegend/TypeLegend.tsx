import styles from './TypeLegend.module.css'

export interface LegendItem {
  id: string
  label: string
  /** Variable CSS del color (ej. `var(--color-type-heavy)`). */
  color: string
}

export interface TypeLegendProps {
  items: readonly LegendItem[]
}

/** Leyenda de colores del mapa. */
export function TypeLegend({ items }: TypeLegendProps) {
  return (
    <ul className={styles.legend} aria-label="Leyenda de colores por tipo de mercancía">
      {items.map((item) => (
        <li key={item.id} className={styles.item}>
          <span className={styles.swatch} style={{ background: item.color }} aria-hidden="true" />
          {item.label}
        </li>
      ))}
    </ul>
  )
}
