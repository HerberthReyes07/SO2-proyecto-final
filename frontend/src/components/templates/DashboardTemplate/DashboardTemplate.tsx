import type { ReactNode } from 'react'
import styles from './DashboardTemplate.module.css'

export interface DashboardTemplateProps {
  title: string
  /** Controles e indicadores (ej. reloj, estado de la conexión) que se muestran a la derecha del título. */
  status?: ReactNode
  children: ReactNode
}

/** Esqueleto de la pantalla: encabezado y área de contenido. No conoce datos, solo distribución. */
export function DashboardTemplate({ title, status, children }: DashboardTemplateProps) {
  return (
    <div className={styles.layout}>
      <header className={styles.header}>
        <h1 className={styles.title}>{title}</h1>
        <div className={styles.status}>{status}</div>
      </header>
      <main className={styles.content}>{children}</main>
    </div>
  )
}
