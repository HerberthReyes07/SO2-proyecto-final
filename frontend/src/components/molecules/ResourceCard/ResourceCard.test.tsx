import { render, screen } from '@testing-library/react'
import { ResourceCard } from './ResourceCard'

describe('ResourceCard', () => {
  it('shows usage, status and who holds the resource', () => {
    render(<ResourceCard label="Montacargas" capacity={3} holders={['ORD-1', 'ORD-2']} waiting={['ORD-3']} utilization={0.4} />)

    expect(screen.getByText('2 / 3 en uso')).toBeInTheDocument()
    expect(screen.getByText('En uso')).toBeInTheDocument()
    expect(screen.getByText('ORD-1')).toBeInTheDocument()
    expect(screen.getByText(/1 en espera/)).toBeInTheDocument()
  })

  it('shows the average utilization as a percentage', () => {
    render(<ResourceCard label="Montacargas" capacity={3} holders={[]} waiting={[]} utilization={0.734} />)

    expect(screen.getByText('Ocupación promedio: 73%')).toBeInTheDocument()
  })

  it('marks an idle resource as available', () => {
    render(<ResourceCard label="Sistema de escaneo" capacity={1} holders={[]} waiting={[]} utilization={0} />)

    expect(screen.getByText('Disponible')).toBeInTheDocument()
  })
})
