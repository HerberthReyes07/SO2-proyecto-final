import { Badge, type BadgeTone } from '@/components/atoms/Badge'
import type { LevelStats } from '@/types/simulation'
import { formatSimulatedDuration } from '@/utils/formatSimulatedDuration'
import { formatPercent } from '@/utils/formatStats'
import styles from './LevelStatsTable.module.css'

export interface LevelStatsTableProps {
  levels: readonly LevelStats[]
  /** Diccionario id de nivel de servicio → nombre mostrado. */
  serviceLevelNames: Record<string, string>
}

const GOOD_ON_TIME = 0.9
const FAIR_ON_TIME = 0.7
const NO_DATA = '—'

/** Verde si casi todos se atienden a tiempo, ámbar si una parte importante se retrasa, rojo si la mayoría. */
function onTimeTone(ratio: number): BadgeTone {
  if (ratio >= GOOD_ON_TIME) return 'success'
  if (ratio >= FAIR_ON_TIME) return 'warning'
  return 'danger'
}

/** Tabla con las estadísticas de cada nivel de servicio. Un nivel sin pedidos despachados muestra "—". */
export function LevelStatsTable({ levels, serviceLevelNames }: LevelStatsTableProps) {
  return (
    <div className={styles.wrapper}>
      <table className={styles.table}>
        <thead>
          <tr>
            <th scope="col">Nivel</th>
            <th scope="col">Despachados</th>
            <th scope="col">Espera promedio</th>
            <th scope="col">Espera máxima</th>
            <th scope="col">Proceso promedio</th>
            <th scope="col">Atendidos a tiempo</th>
          </tr>
        </thead>
        <tbody>
          {levels.map((stats) => {
            const hasData = stats.completed > 0
            return (
              <tr key={stats.level}>
                <th scope="row">{serviceLevelNames[stats.level] ?? stats.level}</th>
                <td>{stats.completed}</td>
                <td>{hasData ? formatSimulatedDuration(stats.avgWaitMillis) : NO_DATA}</td>
                <td>{hasData ? formatSimulatedDuration(stats.maxWaitMillis) : NO_DATA}</td>
                <td>{hasData ? formatSimulatedDuration(stats.avgProcessingMillis) : NO_DATA}</td>
                <td>
                  {hasData ? <Badge tone={onTimeTone(stats.onTimeRatio)}>{formatPercent(stats.onTimeRatio)}</Badge> : NO_DATA}
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
