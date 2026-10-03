import styles from './TabBar.module.css'

export interface Tab {
  id: string
  label: string
}

export interface TabBarProps {
  tabs: readonly Tab[]
  activeId: string
  onChange: (id: string) => void
  /** Prefijo para los ids de las pestañas; enlaza cada una con su panel (`aria-controls`). */
  idPrefix: string
}

/** Barra de pestañas accesible (rol tablist). Solo elige la pestaña; mostrar su contenido es de quien la usa. */
export function TabBar({ tabs, activeId, onChange, idPrefix }: TabBarProps) {
  return (
    <div className={styles.bar} role="tablist">
      {tabs.map((tab) => {
        const selected = tab.id === activeId
        return (
          <button
            key={tab.id}
            id={`${idPrefix}-tab-${tab.id}`}
            className={`${styles.tab} ${selected ? styles.selected : ''}`}
            type="button"
            role="tab"
            aria-selected={selected}
            aria-controls={`${idPrefix}-panel`}
            onClick={() => onChange(tab.id)}
          >
            {tab.label}
          </button>
        )
      })}
    </div>
  )
}
