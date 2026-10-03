import { useState } from 'react'
import { Badge, type BadgeTone } from '@/components/atoms/Badge'
import { Button } from '@/components/atoms/Button'
import { Input } from '@/components/atoms/Input'
import { ProgressBar } from '@/components/atoms/ProgressBar'
import { ComparisonMatrix, type MatrixColumn } from '@/components/molecules/ComparisonMatrix'
import { FormField } from '@/components/molecules/FormField'
import { IntensitySelector } from '@/components/molecules/IntensitySelector'
import {
  BENCHMARK_INTENSITY_PRESETS,
  DEFAULT_BENCHMARK_ORDERS,
  DEFAULT_BENCHMARK_ORDERS_PER_MINUTE,
  MAX_BENCHMARK_ORDERS,
  MIN_BENCHMARK_ORDERS,
} from '@/constants/benchmarkPresets'
import type { BenchmarkPhase, BenchmarkState, StartBenchmarkRequest } from '@/types/benchmark'
import type { PolicyId, PolicyInfo } from '@/types/catalog'
import { buildLevelRows, buildOverviewRows, policyNames, summarize } from '@/utils/benchmarkReport'
import { parseBoundedInteger, parseSeed } from '@/utils/formatStats'
import styles from './BenchmarkPanel.module.css'

export interface BenchmarkPanelProps {
  benchmark: BenchmarkState
  /** Políticas con su nombre; también define el orden de las columnas. */
  policies: readonly PolicyInfo[]
  /** Política que usa el sistema en vivo ahora. */
  currentPolicy: PolicyId
  /** Diccionario id de nivel de servicio → nombre mostrado. */
  serviceLevelNames: Record<string, string>
  onStart: (request: StartBenchmarkRequest) => void
  onCancel: () => void
  /** Cambia la política del sistema en vivo a la elegida en la comparación. */
  onUsePolicy: (policy: PolicyId) => void
  pending: boolean
  error?: string | null
}

const PHASE_BADGES: Record<BenchmarkPhase, { label: string; tone: BadgeTone }> = {
  IDLE: { label: 'Sin comparar', tone: 'neutral' },
  RUNNING: { label: 'Comparando…', tone: 'warning' },
  DONE: { label: 'Terminada', tone: 'success' },
  CANCELLED: { label: 'Cancelada', tone: 'neutral' },
  FAILED: { label: 'Falló', tone: 'danger' },
}

/**
 * Comparador de políticas de atención: lanza una misma carga contra cada política, en una simulación aparte
 * que no toca el sistema en vivo, y muestra los resultados lado a lado a medida que van terminando.
 */
export function BenchmarkPanel({
  benchmark,
  policies,
  currentPolicy,
  serviceLevelNames,
  onStart,
  onCancel,
  onUsePolicy,
  pending,
  error,
}: BenchmarkPanelProps) {
  const [ordersText, setOrdersText] = useState(String(DEFAULT_BENCHMARK_ORDERS))
  const [ordersPerMinute, setOrdersPerMinute] = useState(DEFAULT_BENCHMARK_ORDERS_PER_MINUTE)
  const [seedText, setSeedText] = useState('')

  const orders = parseBoundedInteger(ordersText, MIN_BENCHMARK_ORDERS, MAX_BENCHMARK_ORDERS)
  const seed = parseSeed(seedText)
  const ordersInvalid = orders === null
  const seedInvalid = seedText.trim() !== '' && seed === null
  const running = benchmark.phase === 'RUNNING'

  const handleStart = () => {
    if (orders === null || seedInvalid) return
    onStart({ orders, ordersPerMinute, seed: seed ?? undefined })
  }

  const names = policyNames(policies)
  const plainColumns: MatrixColumn[] = benchmark.results.map((result) => ({
    id: result.policy,
    label: names[result.policy] ?? result.policy,
  }))
  // La acción de cambiar de política va solo en la primera tabla: repetirla en las tres sería ruido.
  const actionColumns: MatrixColumn[] = plainColumns.map((column) => ({
    ...column,
    extra:
      column.id === currentPolicy ? (
        <Badge tone="success">En uso</Badge>
      ) : (
        <Button disabled={pending} onClick={() => onUsePolicy(column.id as PolicyId)}>
          Usar esta política
        </Button>
      ),
  }))
  const summary = summarize(benchmark.results, names, serviceLevelNames)
  const badge = PHASE_BADGES[benchmark.phase]

  return (
    <div className={styles.panel}>
      <section className={styles.card} aria-labelledby="benchmark-title">
        <header className={styles.header}>
          <h2 id="benchmark-title" className={styles.title}>
            Comparador de políticas
          </h2>
          <Badge tone={badge.tone}>{badge.label}</Badge>
        </header>
        <p className={styles.note}>
          Corre la misma carga de pedidos con cada política de atención, de a una, en una simulación aparte (no afecta
          los pedidos, el inventario ni las estadísticas en vivo). Usa stock abundante y sin camiones, para que la
          única diferencia entre corridas sea el criterio de orden. Tarda cerca de un minuto; para mejores resultados,
          detenga el generador automático mientras compara.
        </p>

        <div className={styles.fields}>
          <FormField
            label="Pedidos de la carga"
            htmlFor="benchmark-orders"
            error={ordersInvalid ? `Debe ser un entero entre ${MIN_BENCHMARK_ORDERS} y ${MAX_BENCHMARK_ORDERS}.` : null}
          >
            <Input
              id="benchmark-orders"
              inputMode="numeric"
              value={ordersText}
              invalid={ordersInvalid}
              disabled={running}
              onChange={(event) => setOrdersText(event.target.value)}
            />
          </FormField>
          <FormField
            label="Semilla (opcional)"
            htmlFor="benchmark-seed"
            error={seedInvalid ? 'La semilla debe ser un número entero.' : null}
          >
            <Input
              id="benchmark-seed"
              inputMode="numeric"
              placeholder="Vacío: se elige una al azar"
              value={seedText}
              invalid={seedInvalid}
              disabled={running}
              onChange={(event) => setSeedText(event.target.value)}
            />
          </FormField>
        </div>
        <IntensitySelector presets={BENCHMARK_INTENSITY_PRESETS} value={ordersPerMinute} onChange={setOrdersPerMinute} />

        <div className={styles.actions}>
          <Button variant="primary" disabled={pending || running || ordersInvalid || seedInvalid} onClick={handleStart}>
            Comparar políticas
          </Button>
          {running && <Button onClick={onCancel}>Cancelar</Button>}
        </div>

        {running && <Progress benchmark={benchmark} names={names} />}
        {benchmark.phase === 'FAILED' && (
          <p className={styles.error} role="alert">
            La comparación falló: {benchmark.error ?? 'error desconocido'}
          </p>
        )}
        {error && (
          <p className={styles.error} role="alert">
            {error}
          </p>
        )}
      </section>

      {benchmark.results.length > 0 && benchmark.params && (
        <section className={styles.card} aria-labelledby="benchmark-results-title">
          <h3 id="benchmark-results-title" className={styles.subtitle}>
            Resultados
          </h3>
          <p className={styles.note}>
            Carga: {benchmark.params.orders} pedidos, {benchmark.params.ordersPerMinute} por minuto simulado, semilla{' '}
            <strong>{benchmark.params.seed}</strong> (la misma semilla repite la misma carga).
            {benchmark.phase !== 'DONE' && ' Resultados parciales.'}
          </p>
          {summary.length > 0 && (
            <ul className={styles.summary} aria-label="Resumen de la comparación">
              {summary.map((sentence) => (
                <li key={sentence}>{sentence}</li>
              ))}
            </ul>
          )}
          <ComparisonMatrix caption="Métricas generales" columns={actionColumns} rows={buildOverviewRows(benchmark.results)} />
          <ComparisonMatrix
            caption="Espera promedio por nivel de servicio"
            columns={plainColumns}
            rows={buildLevelRows(benchmark.results, serviceLevelNames, 'avgWait')}
          />
          <ComparisonMatrix
            caption="Espera máxima por nivel de servicio (revela inanición)"
            columns={plainColumns}
            rows={buildLevelRows(benchmark.results, serviceLevelNames, 'maxWait')}
          />
          <p className={styles.note}>
            El verde marca el mejor valor de cada fila. Una política que favorece a un nivel lo paga con otro: mire
            la espera máxima de los niveles bajos para detectar inanición.
          </p>
        </section>
      )}
    </div>
  )
}

interface ProgressProps {
  benchmark: BenchmarkState
  names: Record<string, string>
}

/** Avance global: políticas terminadas más la fracción de pedidos de la que está en marcha. */
function Progress({ benchmark, names }: ProgressProps) {
  const ordersPerPolicy = benchmark.params?.orders ?? 0
  const done = benchmark.policiesDone * ordersPerPolicy + benchmark.ordersCompleted
  const current = benchmark.currentPolicy ? (names[benchmark.currentPolicy] ?? benchmark.currentPolicy) : '…'

  return (
    <div className={styles.progress}>
      <ProgressBar value={done} max={benchmark.policiesTotal * ordersPerPolicy} label="Avance de la comparación" />
      <p className={styles.note}>
        Política {Math.min(benchmark.policiesDone + 1, benchmark.policiesTotal)} de {benchmark.policiesTotal}: {current} ·{' '}
        {benchmark.ordersCompleted} de {ordersPerPolicy} pedidos despachados
      </p>
    </div>
  )
}
