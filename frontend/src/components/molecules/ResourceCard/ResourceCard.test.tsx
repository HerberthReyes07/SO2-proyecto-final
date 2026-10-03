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

  it('names the operations that are waiting, apart from the ones that hold the resource', () => {
    render(<ResourceCard label="Montacargas" capacity={3} holders={['ORD-1']} waiting={['TRK-1', 'ORD-3']} utilization={0.4} />)

    expect(screen.getByRole('list', { name: 'Operaciones usando Montacargas' })).toHaveTextContent('ORD-1')
    expect(screen.getByRole('list', { name: 'Operaciones esperando Montacargas' })).toHaveTextContent('TRK-1')
    expect(screen.getByRole('list', { name: 'Operaciones esperando Montacargas' })).toHaveTextContent('ORD-3')
  })

  it('summarizes a long queue instead of listing everybody', () => {
    const waiting = Array.from({ length: 10 }, (_, i) => `ORD-${i}`)
    render(<ResourceCard label="Sistema de escaneo" capacity={1} holders={[]} waiting={waiting} utilization={1} />)

    expect(screen.getByText('+4 más')).toBeInTheDocument()
    expect(screen.queryByText('ORD-9')).not.toBeInTheDocument()
  })

  it('shows no list of waiting operations when nobody waits', () => {
    render(<ResourceCard label="Montacargas" capacity={3} holders={[]} waiting={[]} utilization={0} />)

    expect(screen.queryByRole('list', { name: /esperando/ })).not.toBeInTheDocument()
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
