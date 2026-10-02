import { Button } from '@/components/atoms/Button'
import { LevelStatsTable } from '@/components/molecules/LevelStatsTable'
import { StatCard } from '@/components/molecules/StatCard'
import type { StatsSnapshot } from '@/types/simulation'
import { formatSimulatedDuration } from '@/utils/formatSimulatedDuration'
import { formatPercent, formatThroughput } from '@/utils/formatStats'
import styles from './StatsPanel.module.css'

export interface StatsPanelProps {
  stats: StatsSnapshot
  /** Diccionario id de nivel de servicio → nombre mostrado. */
  serviceLevelNames: Record<string, string>
  onReset: () => void
  resetting?: boolean
  error?: string | null
}

const NO_DATA = '—'

/** Estadísticas generales y por nivel de servicio de los pedidos despachados. */
export function StatsPanel({ stats, serviceLevelNames, onReset, resetting, error }: StatsPanelProps) {
  const { totals } = stats
  const hasData = totals.completed > 0

  return (
    <section aria-labelledby="stats-title">
      <header className={styles.header}>
        <h2 id="stats-title" className={styles.title}>
          Estadísticas
        </h2>
        <Button disabled={resetting} onClick={onReset}>
          Reiniciar estadísticas
        </Button>
      </header>

      <div className={styles.cards} role="group" aria-label="Indicadores generales">
        <StatCard label="Pedidos despachados" value={String(totals.completed)} />
        <StatCard
          label="Espera promedio"
          value={hasData ? formatSimulatedDuration(totals.avgWaitMillis) : NO_DATA}
          hint="en la lista de espera"
        />
        <StatCard
          label="Proceso promedio"
          value={hasData ? formatSimulatedDuration(totals.avgProcessingMillis) : NO_DATA}
          hint="con encargado asignado"
        />
        <StatCard
          label="Atendidos a tiempo"
          value={hasData ? formatPercent(totals.onTimeRatio) : NO_DATA}
          hint="dentro del objetivo de su nivel"
        />
        <StatCard label="Rendimiento" value={formatThroughput(totals.throughputPerMinute)} hint="pedidos por minuto simulado" />
        <StatCard label="Tiempo medido" value={formatSimulatedDuration(totals.elapsedMillis)} hint="desde el último reinicio" />
      </div>

      <LevelStatsTable levels={stats.levels} serviceLevelNames={serviceLevelNames} />

      {error && (
        <p className={styles.error} role="alert">
          {error}
        </p>
      )}
    </section>
  )
}
