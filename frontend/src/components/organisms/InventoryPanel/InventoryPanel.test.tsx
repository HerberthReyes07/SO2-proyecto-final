import { render, screen } from '@testing-library/react'
import { InventoryPanel } from './InventoryPanel'

describe('InventoryPanel', () => {
  it('titles the section and lists every product', () => {
    render(
      <InventoryPanel
        stock={[
          { productId: 'P-001', available: 300, reserved: 0 },
          { productId: 'P-007', available: 12, reserved: 3 },
        ]}
        productNames={{ 'P-001': 'Camiseta', 'P-007': 'Refrigeradora' }}
      />,
    )

    expect(screen.getByRole('heading', { name: 'Inventario' })).toBeInTheDocument()
    expect(screen.getByRole('rowheader', { name: 'Camiseta' })).toBeInTheDocument()
    expect(screen.getByRole('rowheader', { name: 'Refrigeradora' })).toBeInTheDocument()
  })
})
