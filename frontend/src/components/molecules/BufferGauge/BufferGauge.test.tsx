import { render, screen } from '@testing-library/react'
import type { ReceptionBufferState } from '@/types/simulation'
import { BufferGauge } from './BufferGauge'

const buffer = (overrides: Partial<ReceptionBufferState> = {}): ReceptionBufferState => ({
  capacity: 10,
  size: 4,
  blockedProducers: 0,
  accepted: 25,
  delivered: 21,
  ...overrides,
})

describe('BufferGauge', () => {
  it('shows how full the buffer is and its counters', () => {
    render(<BufferGauge buffer={buffer()} />)

    expect(screen.getByRole('progressbar', { name: 'Ocupación del buffer de recepción' })).toHaveAttribute(
      'aria-valuenow',
      '4',
    )
    expect(screen.getByText('4 / 10 mensajes · 25 recibidos · 21 procesados')).toBeInTheDocument()
  })

  it('does not mention backpressure while nobody waits for space', () => {
    render(<BufferGauge buffer={buffer()} />)

    expect(screen.queryByText(/Contrapresión/)).not.toBeInTheDocument()
  })

  it('warns about backpressure when trucks are waiting for space', () => {
    render(<BufferGauge buffer={buffer({ size: 10, blockedProducers: 3 })} />)

    expect(screen.getByText('Contrapresión: 3 esperando espacio')).toBeInTheDocument()
  })
})
