import { fireEvent, render, screen, within } from '@testing-library/react'
import type { Lot } from '@/types/simulation'
import { LotTable } from './LotTable'

const LOTS: Lot[] = [
  { lotId: 'INIT-P-001', productId: 'P-001', units: 300, locations: [0], runs: 1 },
  { lotId: 'LOT-TRK-0001', productId: 'P-007', units: 40, locations: [3, 4, 9], runs: 2 },
]
const NAMES = { 'P-001': 'Camiseta', 'P-007': 'Refrigeradora' }

const rowOf = (name: string) => screen.getByRole('row', { name: new RegExp(`^${name}`) })

describe('LotTable', () => {
  it('lists each lot with its product, units and number of locations', () => {
    render(<LotTable lots={LOTS} productNames={NAMES} highlightedLotId={null} onHighlight={() => {}} />)

    const row = within(rowOf('LOT-TRK-0001'))
    expect(row.getByText('Refrigeradora')).toBeInTheDocument()
    expect(row.getByText('40')).toBeInTheDocument()
    expect(row.getByText('3')).toBeInTheDocument()
  })

  it('tells a lot that is all together from one that is scattered', () => {
    render(<LotTable lots={LOTS} productNames={NAMES} highlightedLotId={null} onHighlight={() => {}} />)

    expect(within(rowOf('INIT-P-001')).getByText('Junto')).toBeInTheDocument()
    expect(within(rowOf('LOT-TRK-0001')).getByText('Disperso en 2 tramos')).toBeInTheDocument()
  })

  it('reports the lot under the pointer so the map can highlight its locations', () => {
    const onHighlight = vi.fn()
    render(<LotTable lots={LOTS} productNames={NAMES} highlightedLotId={null} onHighlight={onHighlight} />)

    fireEvent.mouseEnter(rowOf('LOT-TRK-0001'))
    fireEvent.mouseLeave(rowOf('LOT-TRK-0001'))

    expect(onHighlight).toHaveBeenNthCalledWith(1, 'LOT-TRK-0001')
    expect(onHighlight).toHaveBeenNthCalledWith(2, null)
  })

  it('does the same for keyboard users', () => {
    const onHighlight = vi.fn()
    render(<LotTable lots={LOTS} productNames={NAMES} highlightedLotId={null} onHighlight={onHighlight} />)

    fireEvent.focus(rowOf('INIT-P-001'))

    expect(onHighlight).toHaveBeenCalledWith('INIT-P-001')
  })

  it('falls back to the product id when the name is unknown', () => {
    render(<LotTable lots={[{ ...LOTS[0], productId: 'P-999' }]} productNames={{}} highlightedLotId={null} onHighlight={() => {}} />)

    expect(screen.getByText('P-999')).toBeInTheDocument()
  })
})
