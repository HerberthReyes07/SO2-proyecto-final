import type { ReactNode } from 'react'
import styles from './DashboardTemplate.module.css'

export interface DashboardTemplateProps {
  title: string
  /** Controles e indicadores (ej. reloj, estado de la conexión) que se muestran a la derecha del título. */
  status?: ReactNode
  /** Columna lateral (formularios de entrada). En pantallas angostas pasa a mostrarse debajo del contenido. */
  sidebar?: ReactNode
  children: ReactNode
}

/** Esqueleto de la pantalla: encabezado, contenido principal y columna lateral. No conoce datos, solo distribución. */
export function DashboardTemplate({ title, status, sidebar, children }: DashboardTemplateProps) {
  return (
    <div className={styles.layout}>
      <header className={styles.header}>
        <h1 className={styles.title}>{title}</h1>
        <div className={styles.status}>{status}</div>
      </header>
      <div className={styles.body}>
        <main className={styles.content}>{children}</main>
        {sidebar && <aside className={styles.sidebar}>{sidebar}</aside>}
      </div>
    </div>
  )
}
