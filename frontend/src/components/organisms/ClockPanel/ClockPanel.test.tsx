import { fireEvent, render, screen } from '@testing-library/react'
import { ClockPanel } from './ClockPanel'

const RUNNING = { simulatedMillis: 90_000, speed: 60, paused: false }

describe('ClockPanel', () => {
  it('shows the simulated time', () => {
    render(<ClockPanel clock={RUNNING} onSpeedChange={() => {}} onPausedChange={() => {}} />)

    expect(screen.getByText('Día 1 · 00:01:30')).toBeInTheDocument()
  })

  it('offers to pause a running clock', () => {
    const onPausedChange = vi.fn()
    render(<ClockPanel clock={RUNNING} onSpeedChange={() => {}} onPausedChange={onPausedChange} />)

    fireEvent.click(screen.getByRole('button', { name: 'Pausar' }))

    expect(onPausedChange).toHaveBeenCalledWith(true)
  })

  it('shows the paused state and offers to resume', () => {
    const onPausedChange = vi.fn()
    render(<ClockPanel clock={{ ...RUNNING, paused: true }} onSpeedChange={() => {}} onPausedChange={onPausedChange} />)

    expect(screen.getByText('En pausa')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Reanudar' }))
    expect(onPausedChange).toHaveBeenCalledWith(false)
  })

  it('reports a speed change', () => {
    const onSpeedChange = vi.fn()
    render(<ClockPanel clock={RUNNING} onSpeedChange={onSpeedChange} onPausedChange={() => {}} />)

    fireEvent.click(screen.getByRole('button', { name: '×300' }))

    expect(onSpeedChange).toHaveBeenCalledWith(300)
  })

  it('announces errors', () => {
    render(<ClockPanel clock={RUNNING} onSpeedChange={() => {}} onPausedChange={() => {}} error="Velocidad inválida" />)

    expect(screen.getByRole('alert')).toHaveTextContent('Velocidad inválida')
  })
})
