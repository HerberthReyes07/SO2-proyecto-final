import { render, screen, within } from '@testing-library/react'
import type { StockItem } from '@/types/simulation'
import { StockTable } from './StockTable'

const NAMES = { 'P-001': 'Camiseta', 'P-007': 'Refrigeradora', 'P-010': 'Medicamentos' }
const STOCK: StockItem[] = [
  { productId: 'P-001', available: 300, reserved: 12 },
  { productId: 'P-007', available: 0, reserved: 5 },
  { productId: 'P-010', available: 20, reserved: 0 },
]

const rowOf = (name: string) => screen.getByRole('row', { name: new RegExp(`^${name}`) })

describe('StockTable', () => {
  it('shows the available and reserved units of each product by name', () => {
    render(<StockTable stock={STOCK} productNames={NAMES} />)

    const row = within(rowOf('Camiseta'))
    expect(row.getByText('300')).toBeInTheDocument()
    expect(row.getByText('12')).toBeInTheDocument()
  })

  it('flags exhausted and low products but not the ones with plenty', () => {
    render(<StockTable stock={STOCK} productNames={NAMES} />)

    expect(within(rowOf('Refrigeradora')).getByText('Agotado')).toBeInTheDocument()
    expect(within(rowOf('Medicamentos')).getByText('Bajo')).toBeInTheDocument()
    expect(within(rowOf('Camiseta')).queryByText(/Agotado|Bajo/)).not.toBeInTheDocument()
  })

  it('falls back to the product id when the name is unknown', () => {
    render(<StockTable stock={[{ productId: 'P-999', available: 1, reserved: 0 }]} productNames={{}} />)

    expect(screen.getByRole('rowheader', { name: 'P-999' })).toBeInTheDocument()
  })
})
