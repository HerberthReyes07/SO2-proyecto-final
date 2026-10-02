import { fireEvent, render, screen, within } from '@testing-library/react'
import type { StatsSnapshot } from '@/types/simulation'
import { StatsPanel } from './StatsPanel'

const level = (id: StatsSnapshot['levels'][number]['level']) => ({
  level: id,
  completed: 0,
  avgWaitMillis: 0,
  maxWaitMillis: 0,
  avgProcessingMillis: 0,
  onTimeRatio: 0,
})

const EMPTY: StatsSnapshot = {
  totals: { completed: 0, avgWaitMillis: 0, avgProcessingMillis: 0, onTimeRatio: 0, throughputPerMinute: 0, elapsedMillis: 0 },
  levels: [level('EXPRESS'), level('ECONOMY')],
}

const WITH_DATA: StatsSnapshot = {
  totals: {
    completed: 30,
    avgWaitMillis: 150_000,
    avgProcessingMillis: 330_000,
    onTimeRatio: 0.8,
    throughputPerMinute: 1.46,
    elapsedMillis: 1_230_000,
  },
  levels: [{ ...level('EXPRESS'), completed: 30, avgWaitMillis: 150_000, maxWaitMillis: 400_000, avgProcessingMillis: 330_000, onTimeRatio: 0.8 }],
}

/** Valor mostrado en la tarjeta con esa etiqueta; se acota a las tarjetas porque la tabla repite algunas etiquetas. */
const cardValue = (label: string) =>
  within(screen.getByRole('group', { name: 'Indicadores generales' })).getByText(label).nextSibling

describe('StatsPanel', () => {
  it('shows the overall figures with readable units', () => {
    render(<StatsPanel stats={WITH_DATA} serviceLevelNames={{ EXPRESS: 'Exprés' }} onReset={() => {}} />)

    expect(cardValue('Pedidos despachados')).toHaveTextContent('30')
    expect(cardValue('Espera promedio')).toHaveTextContent('2 min 30 s')
    expect(cardValue('Proceso promedio')).toHaveTextContent('5 min 30 s')
    expect(cardValue('Atendidos a tiempo')).toHaveTextContent('80%')
    expect(cardValue('Rendimiento')).toHaveTextContent('1,5')
    expect(cardValue('Tiempo medido')).toHaveTextContent('20 min 30 s')
  })

  it('shows dashes instead of misleading zeros when nothing has been dispatched yet', () => {
    render(<StatsPanel stats={EMPTY} serviceLevelNames={{}} onReset={() => {}} />)

    expect(cardValue('Espera promedio')).toHaveTextContent('—')
    expect(cardValue('Atendidos a tiempo')).toHaveTextContent('—')
    expect(cardValue('Pedidos despachados')).toHaveTextContent('0')
  })

  it('includes the per-level table', () => {
    render(<StatsPanel stats={WITH_DATA} serviceLevelNames={{ EXPRESS: 'Exprés' }} onReset={() => {}} />)

    expect(screen.getByRole('table')).toBeInTheDocument()
    expect(screen.getByRole('rowheader', { name: 'Exprés' })).toBeInTheDocument()
  })

  it('asks to reset the statistics', () => {
    const onReset = vi.fn()
    render(<StatsPanel stats={EMPTY} serviceLevelNames={{}} onReset={onReset} />)

    fireEvent.click(screen.getByRole('button', { name: 'Reiniciar estadísticas' }))

    expect(onReset).toHaveBeenCalledTimes(1)
  })

  it('disables the reset button while resetting and shows errors', () => {
    render(<StatsPanel stats={EMPTY} serviceLevelNames={{}} onReset={() => {}} resetting error="Error 500" />)

    expect(screen.getByRole('button', { name: 'Reiniciar estadísticas' })).toBeDisabled()
    expect(screen.getByRole('alert')).toHaveTextContent('Error 500')
  })
})
