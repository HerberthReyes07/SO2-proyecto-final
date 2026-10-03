import type { ReactNode } from 'react'
import type { MatrixRow } from '@/utils/benchmarkReport'
import styles from './ComparisonMatrix.module.css'

export interface MatrixColumn {
  id: string
  label: string
  /** Contenido opcional bajo el nombre de la columna (una etiqueta de estado, un botón de acción). */
  extra?: ReactNode
}

export interface ComparisonMatrixProps {
  /** Describe la tabla a lectores de pantalla y la titula visualmente. */
  caption: string
  columns: readonly MatrixColumn[]
  rows: readonly MatrixRow[]
}

/** Tabla de métricas (filas) por política (columnas) que resalta el mejor valor de cada fila. */
export function ComparisonMatrix({ caption, columns, rows }: ComparisonMatrixProps) {
  return (
    <div className={styles.wrapper}>
      <table className={styles.table}>
        <caption className={styles.caption}>{caption}</caption>
        <thead>
          <tr>
            <th scope="col">Métrica</th>
            {columns.map((column) => (
              <th key={column.id} scope="col">
                <span className={styles.columnLabel}>{column.label}</span>
                {column.extra && <span className={styles.columnExtra}>{column.extra}</span>}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.id}>
              <th scope="row">{row.label}</th>
              {row.cells.map((cell, index) => (
                <td key={columns[index]?.id ?? index} className={cell.best ? styles.best : undefined}>
                  {cell.text}
                  {/* El resaltado es solo de color: este texto lo comunica también a lectores de pantalla. */}
                  {cell.best && <span className={styles.srOnly}> (mejor)</span>}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
