import type { PolicyResult } from '@/types/benchmark'
import type { PolicyInfo } from '@/types/catalog'
import type { LevelStats } from '@/types/simulation'
import { buildLevelRows, buildOverviewRows, markBest, policyNames, summarize } from './benchmarkReport'

const LEVEL_NAMES = { EXPRESS: 'Exprés', ECONOMY: 'Económico' }
const NAMES = { FIFO: 'Orden de llegada', STRICT_PRIORITY: 'Prioridad estricta' }

function level(id: LevelStats['level'], overrides: Partial<LevelStats> = {}): LevelStats {
  return { level: id, completed: 10, avgWaitMillis: 60_000, maxWaitMillis: 120_000, avgProcessingMillis: 0, onTimeRatio: 1, ...overrides }
}

function result(policy: PolicyResult['policy'], overrides: { avg?: number; onTime?: number; levels?: LevelStats[]; utilization?: number } = {}): PolicyResult {
  return {
    policy,
    durationMillis: 3_600_000,
    workerUtilization: overrides.utilization ?? 0.8,
    stats: {
      totals: {
        completed: 20,
        avgWaitMillis: overrides.avg ?? 60_000,
        avgProcessingMillis: 0,
        onTimeRatio: overrides.onTime ?? 0.5,
        throughputPerMinute: 1.5,
        elapsedMillis: 3_600_000,
      },
      levels: overrides.levels ?? [level('EXPRESS'), level('ECONOMY')],
    },
  }
}

describe('markBest', () => {
  it('marks the lowest value for "min" and every tied one', () => {
    expect(markBest([30, 10, 20, 10], 'min')).toEqual([false, true, false, true])
  })

  it('marks the highest value for "max"', () => {
    expect(markBest([0.5, 0.9, 0.7], 'max')).toEqual([false, true, false])
  })

  it('never marks a missing value', () => {
    expect(markBest([null, 5, 9], 'min')).toEqual([false, true, false])
    expect(markBest([null, null], 'min')).toEqual([false, false])
  })

  it('marks nothing when every value is equal', () => {
    expect(markBest([7, 7, 7], 'min')).toEqual([false, false, false])
  })

  it('treats values within the tolerance of the best as tied with it', () => {
    expect(markBest([55, 54, 54, 64], 'min', 5)).toEqual([true, true, true, false])
  })

  it('marks nothing when every value is within the tolerance', () => {
    expect(markBest([55, 54, 56], 'min', 5)).toEqual([false, false, false])
  })
})

describe('buildOverviewRows', () => {
  it('has one cell per policy and highlights the best of each metric', () => {
    const rows = buildOverviewRows([result('FIFO', { avg: 120_000, onTime: 0.4 }), result('STRICT_PRIORITY', { avg: 60_000, onTime: 0.9 })])

    const byId = Object.fromEntries(rows.map((row) => [row.id, row]))
    expect(byId.avgWait.cells.map((cell) => cell.text)).toEqual(['2 min 0 s', '1 min 0 s'])
    expect(byId.avgWait.cells.map((cell) => cell.best)).toEqual([false, true])
    expect(byId.onTime.cells.map((cell) => cell.text)).toEqual(['40%', '90%'])
    expect(byId.onTime.cells.map((cell) => cell.best)).toEqual([false, true])
  })

  it('uses the largest wait of any level as the worst case', () => {
    const rows = buildOverviewRows([
      result('FIFO', { levels: [level('EXPRESS', { maxWaitMillis: 300_000 }), level('ECONOMY', { maxWaitMillis: 900_000 })] }),
    ])

    expect(rows.find((row) => row.id === 'worstWait')?.cells[0].text).toBe('15 min 0 s')
  })

  it('does not crown a winner for differences as small as scheduling noise', () => {
    const rows = buildOverviewRows([result('FIFO', { avg: 61_000 }), result('STRICT_PRIORITY', { avg: 60_000 })])

    expect(rows.find((row) => row.id === 'avgWait')?.cells.every((cell) => !cell.best)).toBe(true)
  })

  it('does not highlight utilization, since more is neither better nor worse', () => {
    const rows = buildOverviewRows([result('FIFO', { utilization: 0.6 }), result('STRICT_PRIORITY', { utilization: 0.9 })])

    expect(rows.find((row) => row.id === 'utilization')?.cells.every((cell) => !cell.best)).toBe(true)
  })
})

describe('buildLevelRows', () => {
  const fifo = result('FIFO', { levels: [level('EXPRESS', { avgWaitMillis: 600_000 }), level('ECONOMY', { avgWaitMillis: 100_000 })] })
  const strict = result('STRICT_PRIORITY', { levels: [level('EXPRESS', { avgWaitMillis: 30_000 }), level('ECONOMY', { avgWaitMillis: 500_000 })] })

  it('has one row per level, named, and highlights the lowest wait of each', () => {
    const rows = buildLevelRows([fifo, strict], LEVEL_NAMES, 'avgWait')

    expect(rows.map((row) => row.label)).toEqual(['Exprés', 'Económico'])
    expect(rows[0].cells.map((cell) => cell.best)).toEqual([false, true])
    expect(rows[1].cells.map((cell) => cell.best)).toEqual([true, false])
  })

  it('can show the maximum wait instead of the average', () => {
    const rows = buildLevelRows([fifo], LEVEL_NAMES, 'maxWait')

    expect(rows[0].cells[0].text).toBe('2 min 0 s')
  })

  it('shows a dash for a level that had no orders', () => {
    const empty = result('FIFO', { levels: [level('EXPRESS', { completed: 0 })] })

    expect(buildLevelRows([empty], LEVEL_NAMES, 'avgWait')[0].cells[0]).toEqual({ text: '—', best: false })
  })

  it('returns nothing without results', () => {
    expect(buildLevelRows([], LEVEL_NAMES, 'avgWait')).toEqual([])
  })
})

describe('policyNames', () => {
  it('maps each id to its display name', () => {
    const policies: PolicyInfo[] = [{ id: 'FIFO', displayName: 'Orden de llegada', description: '' }]

    expect(policyNames(policies)).toEqual({ FIFO: 'Orden de llegada' })
  })
})

describe('summarize', () => {
  const fifo = result('FIFO', { avg: 120_000, onTime: 0.4, levels: [level('EXPRESS', { avgWaitMillis: 600_000, maxWaitMillis: 900_000 })] })
  const strict = result('STRICT_PRIORITY', {
    avg: 60_000,
    onTime: 0.9,
    levels: [level('EXPRESS', { avgWaitMillis: 30_000, maxWaitMillis: 100_000 })],
  })

  it('says who wins each aspect', () => {
    const sentences = summarize([fifo, strict], NAMES, LEVEL_NAMES)

    expect(sentences).toContain('Menor espera promedio: Prioridad estricta.')
    expect(sentences).toContain('Más pedidos atendidos a tiempo: Prioridad estricta.')
    expect(sentences).toContain('Mejor para Exprés: Prioridad estricta.')
    expect(sentences.some((sentence) => sentence.includes('inanición'))).toBe(true)
  })

  it('names every policy in a tie', () => {
    const tied = result('STRICT_PRIORITY', { avg: 120_000, onTime: 0.4, levels: fifo.stats.levels })
    const worse = result('FIFO', { avg: 300_000, onTime: 0.1, levels: fifo.stats.levels })

    const sentences = summarize([worse, fifo, tied], NAMES, LEVEL_NAMES)

    expect(sentences).toContain('Menor espera promedio: Orden de llegada y Prioridad estricta.')
  })

  it('lists three tied policies with commas and a final "y"', () => {
    const names = { FIFO: 'A', STRICT_PRIORITY: 'B', AGING_PRIORITY: 'C' }
    const worse = result('FIFO', { avg: 600_000 })
    const tied = (policy: PolicyResult['policy']) => result(policy, { avg: 100_000 })

    const sentences = summarize([worse, tied('STRICT_PRIORITY'), tied('AGING_PRIORITY'), tied('EARLIEST_DEADLINE_FIRST')], { ...names, EARLIEST_DEADLINE_FIRST: 'D' }, LEVEL_NAMES)

    expect(sentences).toContain('Menor espera promedio: B, C y D.')
  })

  it('has nothing to compare with fewer than two policies', () => {
    expect(summarize([fifo], NAMES, LEVEL_NAMES)).toEqual([])
  })

  it('skips an aspect without a clear winner', () => {
    const same = result('STRICT_PRIORITY', { avg: 120_000, onTime: 0.4, levels: fifo.stats.levels })

    const sentences = summarize([fifo, same], NAMES, LEVEL_NAMES)

    expect(sentences).toEqual([])
  })
})
