import { fireEvent, render, screen } from '@testing-library/react'
import type { GeneratorState } from '@/types/simulation'
import { GeneratorPanel } from './GeneratorPanel'

const STOPPED: GeneratorState = { enabled: false, ordersPerMinute: 1, seed: 12345, generated: 7 }

function setup(generator: GeneratorState = STOPPED, extra: Partial<React.ComponentProps<typeof GeneratorPanel>> = {}) {
  const props = {
    generator,
    onIntensityChange: vi.fn(),
    onEnabledChange: vi.fn(),
    pending: false,
    ...extra,
  }
  render(<GeneratorPanel {...props} />)
  return props
}

describe('GeneratorPanel', () => {
  it('shows the state, the intensity, the count and the current seed', () => {
    setup()

    expect(screen.getByText('Detenido')).toBeInTheDocument()
    expect(screen.getByText(/1 pedidos por minuto simulado · 7 generados/)).toBeInTheDocument()
    expect(screen.getByText('12345')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Normal' })).toHaveAttribute('aria-pressed', 'true')
  })

  it('starts the generator without a seed when none was typed', () => {
    const { onEnabledChange } = setup()

    fireEvent.click(screen.getByRole('button', { name: 'Iniciar generador' }))

    expect(onEnabledChange).toHaveBeenCalledWith(true, undefined)
  })

  it('starts the generator with the typed seed', () => {
    const { onEnabledChange } = setup()

    fireEvent.change(screen.getByLabelText('Semilla (opcional)'), { target: { value: ' 42 ' } })
    fireEvent.click(screen.getByRole('button', { name: 'Iniciar generador' }))

    expect(onEnabledChange).toHaveBeenCalledWith(true, 42)
  })

  it('refuses to start with a seed that is not an integer', () => {
    const { onEnabledChange } = setup()

    fireEvent.change(screen.getByLabelText('Semilla (opcional)'), { target: { value: 'abc' } })

    expect(screen.getByRole('alert')).toHaveTextContent('entero')
    expect(screen.getByRole('button', { name: 'Iniciar generador' })).toBeDisabled()
    expect(onEnabledChange).not.toHaveBeenCalled()
  })

  it('stops a running generator and locks the seed while it runs', () => {
    const { onEnabledChange } = setup({ ...STOPPED, enabled: true })

    expect(screen.getByText('Activo')).toBeInTheDocument()
    expect(screen.getByLabelText('Semilla (opcional)')).toBeDisabled()
    fireEvent.click(screen.getByRole('button', { name: 'Detener generador' }))

    expect(onEnabledChange).toHaveBeenCalledWith(false)
  })

  it('reports an intensity change', () => {
    const { onIntensityChange } = setup()

    fireEvent.click(screen.getByRole('button', { name: 'Saturación' }))

    expect(onIntensityChange).toHaveBeenCalledWith(3)
  })

  it('shows the server error', () => {
    setup(STOPPED, { error: 'ordersPerMinute must be between 0.05 and 20.0' })

    expect(screen.getByRole('alert')).toHaveTextContent('ordersPerMinute must be between')
  })
})
