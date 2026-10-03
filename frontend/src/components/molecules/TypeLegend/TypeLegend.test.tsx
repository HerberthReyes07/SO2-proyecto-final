import { render, screen } from '@testing-library/react'
import { TypeLegend } from './TypeLegend'

describe('TypeLegend', () => {
  it('lists every merchandise type with its name', () => {
    render(
      <TypeLegend
        items={[
          { id: 'HEAVY', label: 'Pesado', color: 'var(--color-type-heavy)' },
          { id: 'FRAGILE', label: 'Frágil', color: 'var(--color-type-fragile)' },
        ]}
      />,
    )

    expect(screen.getByRole('list', { name: /Leyenda de colores/ })).toBeInTheDocument()
    expect(screen.getByText('Pesado')).toBeInTheDocument()
    expect(screen.getByText('Frágil')).toBeInTheDocument()
  })
})
