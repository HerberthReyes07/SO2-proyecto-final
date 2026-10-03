import styles from './CoffmanChecklist.module.css'

export interface CoffmanItem {
  id: string
  name: string
  explanation: string
}

export interface CoffmanChecklistProps {
  /** Las condiciones de Coffman con cómo se manifiestan; en un deadlock real se cumplen todas. */
  conditions: readonly CoffmanItem[]
}

/** Lista de verificación de las cuatro condiciones de Coffman. */
export function CoffmanChecklist({ conditions }: CoffmanChecklistProps) {
  return (
    <ul className={styles.list} aria-label="Condiciones de Coffman">
      {conditions.map((condition) => (
        <li key={condition.id} className={styles.item}>
          <span className={styles.check} aria-label="Se cumple">
            ✓
          </span>
          <div>
            <strong>{condition.name}</strong>
            <p className={styles.explanation}>{condition.explanation}</p>
          </div>
        </li>
      ))}
    </ul>
  )
}
