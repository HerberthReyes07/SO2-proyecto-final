import { fireEvent, render, screen } from '@testing-library/react'
import type { PolicyInfo } from '@/types/catalog'
import { PolicySelector } from './PolicySelector'

const POLICIES: PolicyInfo[] = [
  { id: 'FIFO', displayName: 'FIFO', description: 'Orden de llegada.' },
  { id: 'STRICT_PRIORITY', displayName: 'Prioridad estricta', description: 'Siempre gana el nivel más urgente.' },
]

describe('PolicySelector', () => {
  it('shows the current policy and explains how it sorts', () => {
    render(<PolicySelector policies={POLICIES} value="STRICT_PRIORITY" onChange={() => {}} />)

    expect(screen.getByLabelText('Política de atención')).toHaveDisplayValue('Prioridad estricta')
    expect(screen.getByText('Siempre gana el nivel más urgente.')).toBeInTheDocument()
  })

  it('reports the chosen policy', () => {
    const onChange = vi.fn()
    render(<PolicySelector policies={POLICIES} value="STRICT_PRIORITY" onChange={onChange} />)

    fireEvent.change(screen.getByLabelText('Política de atención'), { target: { value: 'FIFO' } })

    expect(onChange).toHaveBeenCalledWith('FIFO')
  })

  it('shows the error when the change was rejected', () => {
    render(<PolicySelector policies={POLICIES} value="FIFO" onChange={() => {}} error="No se pudo cambiar" />)

    expect(screen.getByRole('alert')).toHaveTextContent('No se pudo cambiar')
  })
})
