import type { ReactNode } from 'react'
import { TabBar, type Tab } from '@/components/molecules/TabBar'
import styles from './TabbedContent.module.css'

export interface TabbedContentProps {
  tabs: readonly Tab[]
  activeId: string
  onChange: (id: string) => void
  /** Identificador de este grupo de pestañas; se usa para enlazar pestañas y panel. */
  idPrefix: string
  /** Contenido de la pestaña activa. */
  children: ReactNode
}

/** Pestañas con el panel de la activa debajo. El contenido de cada pestaña lo decide quien lo usa. */
export function TabbedContent({ tabs, activeId, onChange, idPrefix, children }: TabbedContentProps) {
  return (
    <div className={styles.wrapper}>
      <TabBar tabs={tabs} activeId={activeId} onChange={onChange} idPrefix={idPrefix} />
      <div className={styles.panel} role="tabpanel" id={`${idPrefix}-panel`} aria-labelledby={`${idPrefix}-tab-${activeId}`}>
        {children}
      </div>
    </div>
  )
}
