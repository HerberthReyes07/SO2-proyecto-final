import { fireEvent, render, screen, within } from '@testing-library/react'
import type { BenchmarkState, PolicyResult } from '@/types/benchmark'
import type { PolicyInfo } from '@/types/catalog'
import type { LevelStats } from '@/types/simulation'
import { BenchmarkPanel } from './BenchmarkPanel'

const POLICIES: PolicyInfo[] = [
  { id: 'FIFO', displayName: 'Orden de llegada', description: '' },
  { id: 'STRICT_PRIORITY', displayName: 'Prioridad estricta', description: '' },
]
const LEVEL_NAMES = { EXPRESS: 'Exprés', ECONOMY: 'Económico' }

function level(id: LevelStats['level'], avg: number): LevelStats {
  return { level: id, completed: 10, avgWaitMillis: avg, maxWaitMillis: avg * 2, avgProcessingMillis: 0, onTimeRatio: 1 }
}

function result(policy: PolicyResult['policy'], expressWait: number, avg: number): PolicyResult {
  return {
    policy,
    durationMillis: 1,
    workerUtilization: 0.8,
    stats: {
      totals: { completed: 20, avgWaitMillis: avg, avgProcessingMillis: 0, onTimeRatio: 0.5, throughputPerMinute: 1, elapsedMillis: 1 },
      levels: [level('EXPRESS', expressWait), level('ECONOMY', 100_000)],
    },
  }
}

const IDLE: BenchmarkState = {
  phase: 'IDLE',
  params: null,
  currentPolicy: null,
  policiesDone: 0,
  policiesTotal: 5,
  ordersCompleted: 0,
  results: [],
  error: null,
}
const PARAMS = { orders: 120, ordersPerMinute: 3, seed: 777 }
const DONE: BenchmarkState = {
  ...IDLE,
  phase: 'DONE',
  params: PARAMS,
  policiesDone: 2,
  results: [result('FIFO', 600_000, 120_000), result('STRICT_PRIORITY', 30_000, 60_000)],
}

function setup(benchmark: BenchmarkState = IDLE, extra: Partial<React.ComponentProps<typeof BenchmarkPanel>> = {}) {
  const props = {
    benchmark,
    policies: POLICIES,
    currentPolicy: 'FIFO' as const,
    serviceLevelNames: LEVEL_NAMES,
    onStart: vi.fn(),
    onCancel: vi.fn(),
    onUsePolicy: vi.fn(),
    pending: false,
    ...extra,
  }
  render(<BenchmarkPanel {...props} />)
  return props
}

describe('BenchmarkPanel', () => {
  it('starts a comparison with the default load', () => {
    const { onStart } = setup()

    fireEvent.click(screen.getByRole('button', { name: 'Comparar políticas' }))

    expect(onStart).toHaveBeenCalledWith({ orders: 120, ordersPerMinute: 3, seed: undefined })
  })

  it('starts with the typed orders, the chosen intensity and the typed seed', () => {
    const { onStart } = setup()

    fireEvent.change(screen.getByLabelText('Pedidos de la carga'), { target: { value: '60' } })
    fireEvent.change(screen.getByLabelText('Semilla (opcional)'), { target: { value: '42' } })
    fireEvent.click(screen.getByRole('button', { name: 'Saturación' }))
    fireEvent.click(screen.getByRole('button', { name: 'Comparar políticas' }))

    expect(onStart).toHaveBeenCalledWith({ orders: 60, ordersPerMinute: 6, seed: 42 })
  })

  it('blocks the start and explains when the orders are out of range', () => {
    const { onStart } = setup()

    fireEvent.change(screen.getByLabelText('Pedidos de la carga'), { target: { value: '5' } })

    expect(screen.getByText(/entero entre 10 y 500/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Comparar políticas' })).toBeDisabled()
    expect(onStart).not.toHaveBeenCalled()
  })

  it('blocks the start when the seed is not a number', () => {
    setup()

    fireEvent.change(screen.getByLabelText('Semilla (opcional)'), { target: { value: 'abc' } })

    expect(screen.getByRole('button', { name: 'Comparar políticas' })).toBeDisabled()
  })

  it('shows the progress and a cancel button while running, and locks the form', () => {
    const running: BenchmarkState = { ...IDLE, phase: 'RUNNING', params: PARAMS, currentPolicy: 'STRICT_PRIORITY', policiesDone: 1, ordersCompleted: 30 }
    const { onCancel } = setup(running)

    expect(screen.getByRole('progressbar', { name: 'Avance de la comparación' })).toHaveAttribute('aria-valuenow', '150')
    expect(screen.getByText(/Política 2 de 5: Prioridad estricta · 30 de 120 pedidos/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Comparar políticas' })).toBeDisabled()
    expect(screen.getByLabelText('Pedidos de la carga')).toBeDisabled()
    fireEvent.click(screen.getByRole('button', { name: 'Cancelar' }))
    expect(onCancel).toHaveBeenCalledTimes(1)
  })

  it('shows no cancel button or results before anything ran', () => {
    setup()

    expect(screen.queryByRole('button', { name: 'Cancelar' })).not.toBeInTheDocument()
    expect(screen.queryByText('Resultados')).not.toBeInTheDocument()
  })

  it('shows the load, a summary and the three tables when done', () => {
    setup(DONE)

    expect(screen.getByText('777')).toBeInTheDocument()
    expect(within(screen.getByRole('list', { name: 'Resumen de la comparación' })).getByText('Menor espera promedio: Prioridad estricta.')).toBeInTheDocument()
    expect(screen.getByRole('table', { name: 'Métricas generales' })).toBeInTheDocument()
    expect(screen.getByRole('table', { name: 'Espera promedio por nivel de servicio' })).toBeInTheDocument()
    expect(screen.getByRole('table', { name: /Espera máxima por nivel de servicio/ })).toBeInTheDocument()
    expect(screen.queryByText(/Resultados parciales/)).not.toBeInTheDocument()
  })

  it('marks partial results while running', () => {
    setup({ ...DONE, phase: 'RUNNING', currentPolicy: 'AGING_PRIORITY' })

    expect(screen.getByText(/Resultados parciales/)).toBeInTheDocument()
  })

  it('marks the policy in use and offers to switch to another', () => {
    const { onUsePolicy } = setup(DONE)

    expect(screen.getByText('En uso')).toBeInTheDocument()
    const use = screen.getAllByRole('button', { name: 'Usar esta política' })
    expect(use).toHaveLength(1)
    fireEvent.click(use[0])
    expect(onUsePolicy).toHaveBeenCalledWith('STRICT_PRIORITY')
  })

  it('reports a failure and keeps the partial results', () => {
    setup({ ...DONE, phase: 'FAILED', error: 'boom' })

    expect(screen.getByRole('alert')).toHaveTextContent('La comparación falló: boom')
    expect(screen.getByRole('table', { name: 'Métricas generales' })).toBeInTheDocument()
  })

  it('shows the action error', () => {
    setup(IDLE, { error: 'A comparison is already running' })

    expect(screen.getByRole('alert')).toHaveTextContent('A comparison is already running')
  })
})
