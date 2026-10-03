import type { PolicyResult } from '@/types/benchmark'
import type { PolicyInfo } from '@/types/catalog'
import type { LevelStats } from '@/types/simulation'
import { formatSimulatedDuration } from '@/utils/formatSimulatedDuration'
import { formatPercent, formatThroughput } from '@/utils/formatStats'

/** Lo que se muestra de cada comparación: una matriz de métricas (filas) por política (columnas). */

export interface MatrixCell {
  text: string
  /** Es el mejor valor de su fila. */
  best: boolean
}

export interface MatrixRow {
  id: string
  label: string
  /** Una celda por política, en el mismo orden que las columnas. */
  cells: MatrixCell[]
}

export type Preference = 'min' | 'max'

const NO_DATA = '—'
const EXPRESS = 'EXPRESS'

/**
 * Diferencias por debajo de estos umbrales se consideran empate: son del orden del ruido de planificación de
 * hilos de la simulación (un par de segundos simulados) y coronar a un "mejor" por ellas sería engañoso.
 */
const DURATION_TOLERANCE_MILLIS = 5_000
const RATIO_TOLERANCE = 0.01
const THROUGHPUT_TOLERANCE = 0.05

/**
 * Marca cuáles valores son los mejores: el menor (`min`) o el mayor (`max`), junto con los que quedan a
 * `tolerance` o menos de él (empate). Un valor nulo (sin datos) nunca es el mejor. Si todos empatan no se marca
 * ninguno: resaltar un empate total solo añade ruido.
 */
export function markBest(values: readonly (number | null)[], preference: Preference, tolerance = 0): boolean[] {
  const present = values.filter((value): value is number => value !== null)
  if (present.length === 0) return values.map(() => false)
  const best = preference === 'min' ? Math.min(...present) : Math.max(...present)
  const tiedWithBest = (value: number) => Math.abs(value - best) <= tolerance
  const allTied = present.every(tiedWithBest)
  return values.map((value) => !allTied && value !== null && tiedWithBest(value))
}

interface Criterion {
  preference: Preference
  tolerance: number
}

const LOWER_DURATION: Criterion = { preference: 'min', tolerance: DURATION_TOLERANCE_MILLIS }
const HIGHER_RATIO: Criterion = { preference: 'max', tolerance: RATIO_TOLERANCE }
const HIGHER_THROUGHPUT: Criterion = { preference: 'max', tolerance: THROUGHPUT_TOLERANCE }

function row(id: string, label: string, values: (number | null)[], criterion: Criterion | null, format: (value: number) => string): MatrixRow {
  const best = criterion ? markBest(values, criterion.preference, criterion.tolerance) : values.map(() => false)
  return { id, label, cells: values.map((value, index) => ({ text: value === null ? NO_DATA : format(value), best: best[index] })) }
}

/** La mayor espera de cualquier pedido, sea de cuál nivel sea: revela si algún pedido quedó esperando de más. */
function worstWait(result: PolicyResult): number {
  return Math.max(0, ...result.stats.levels.map((level) => level.maxWaitMillis))
}

/** Métricas generales de cada política: espera, puntualidad, peor caso, rendimiento y ocupación. */
export function buildOverviewRows(results: readonly PolicyResult[]): MatrixRow[] {
  return [
    row('avgWait', 'Espera promedio', results.map((r) => r.stats.totals.avgWaitMillis), LOWER_DURATION, formatSimulatedDuration),
    row('onTime', 'Atendidos a tiempo', results.map((r) => r.stats.totals.onTimeRatio), HIGHER_RATIO, formatPercent),
    row('worstWait', 'Espera máxima de un pedido', results.map(worstWait), LOWER_DURATION, formatSimulatedDuration),
    row('throughput', 'Rendimiento (pedidos/min)', results.map((r) => r.stats.totals.throughputPerMinute), HIGHER_THROUGHPUT, formatThroughput),
    // La ocupación no se resalta: más ocupación no es mejor ni peor, solo describe cómo se usó a los encargados.
    row('utilization', 'Ocupación de encargados', results.map((r) => r.workerUtilization), null, formatPercent),
  ]
}

export type LevelMetric = 'avgWait' | 'maxWait'

const LEVEL_METRICS: Record<LevelMetric, (stats: LevelStats) => number> = {
  avgWait: (stats) => stats.avgWaitMillis,
  maxWait: (stats) => stats.maxWaitMillis,
}

/** Una fila por nivel de servicio con la espera (promedio o máxima) de cada política; "—" si el nivel no tuvo pedidos. */
export function buildLevelRows(
  results: readonly PolicyResult[],
  serviceLevelNames: Record<string, string>,
  metric: LevelMetric,
): MatrixRow[] {
  const levels = results[0]?.stats.levels ?? []
  return levels.map((level) => {
    const values = results.map((result) => {
      const stats = result.stats.levels.find((candidate) => candidate.level === level.level)
      return stats && stats.completed > 0 ? LEVEL_METRICS[metric](stats) : null
    })
    return row(level.level, serviceLevelNames[level.level] ?? level.level, values, LOWER_DURATION, formatSimulatedDuration)
  })
}

/** Nombre mostrado de cada política (id → nombre); el id si el catálogo aún no cargó. */
export function policyNames(policies: readonly PolicyInfo[]): Record<string, string> {
  return Object.fromEntries(policies.map((policy) => [policy.id, policy.displayName]))
}

/** "A", "A y B", "A, B y C". */
function joinNames(names: string[]): string {
  if (names.length <= 1) return names.join('')
  return `${names.slice(0, -1).join(', ')} y ${names[names.length - 1]}`
}

/** Nombres de las políticas cuyo valor es el mejor; vacío si no hay un ganador claro. */
function winners(results: readonly PolicyResult[], values: (number | null)[], criterion: Criterion, names: Record<string, string>): string[] {
  const best = markBest(values, criterion.preference, criterion.tolerance)
  return results.filter((_, index) => best[index]).map((result) => names[result.policy] ?? result.policy)
}

/**
 * Frases que resumen quién ganó en qué, para no obligar a leer las tablas. Solo tiene sentido comparar con
 * dos o más políticas; con menos (o sin ganador claro en una métrica) esa frase se omite.
 */
export function summarize(
  results: readonly PolicyResult[],
  names: Record<string, string>,
  serviceLevelNames: Record<string, string>,
): string[] {
  if (results.length < 2) return []
  const sentences: string[] = []

  const waitWinners = winners(results, results.map((r) => r.stats.totals.avgWaitMillis), LOWER_DURATION, names)
  if (waitWinners.length > 0) sentences.push(`Menor espera promedio: ${joinNames(waitWinners)}.`)

  const onTimeWinners = winners(results, results.map((r) => r.stats.totals.onTimeRatio), HIGHER_RATIO, names)
  if (onTimeWinners.length > 0) sentences.push(`Más pedidos atendidos a tiempo: ${joinNames(onTimeWinners)}.`)

  const expressWaits = results.map((r) => {
    const express = r.stats.levels.find((level) => level.level === EXPRESS)
    return express && express.completed > 0 ? express.avgWaitMillis : null
  })
  const expressWinners = winners(results, expressWaits, LOWER_DURATION, names)
  if (expressWinners.length > 0) {
    sentences.push(`Mejor para ${serviceLevelNames[EXPRESS] ?? EXPRESS}: ${joinNames(expressWinners)}.`)
  }

  const worstWinners = winners(results, results.map(worstWait), LOWER_DURATION, names)
  if (worstWinners.length > 0) {
    sentences.push(`Deja esperar menos al pedido más rezagado (menor riesgo de inanición): ${joinNames(worstWinners)}.`)
  }
  return sentences
}
