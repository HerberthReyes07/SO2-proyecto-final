import { render, screen } from '@testing-library/react'
import { AisleGrid, type AisleCell } from './AisleGrid'

// 2 posiciones × 3 niveles: índices 0..2 son la posición 1 (niveles 1..3) y 3..5 la posición 2.
const cells: AisleCell[] = Array.from({ length: 6 }, (_, index) => ({
  index,
  label: `celda-${index}`,
  fillRatio: 0,
  highlighted: false,
}))

describe('AisleGrid', () => {
  it('titles the aisle and draws every location', () => {
    render(<AisleGrid title="Pasillo 1" positions={2} levels={3} cells={cells} />)

    expect(screen.getByText('Pasillo 1')).toBeInTheDocument()
    expect(screen.getAllByRole('img')).toHaveLength(6)
  })

  it('puts the highest level on the top row, like a real shelf', () => {
    render(<AisleGrid title="Pasillo 1" positions={2} levels={3} cells={cells} />)

    const order = screen.getAllByRole('img').map((cell) => cell.getAttribute('aria-label'))

    // Fila de arriba (nivel 3): índices 2 y 5; luego nivel 2: 1 y 4; abajo (nivel 1): 0 y 3.
    expect(order).toEqual(['celda-2', 'celda-5', 'celda-1', 'celda-4', 'celda-0', 'celda-3'])
  })

  it('lays out one column per position', () => {
    const { container } = render(<AisleGrid title="Pasillo 1" positions={2} levels={3} cells={cells} />)

    expect(container.querySelector('figure > div')).toHaveStyle({ gridTemplateColumns: 'repeat(2, 1fr)' })
  })
})
