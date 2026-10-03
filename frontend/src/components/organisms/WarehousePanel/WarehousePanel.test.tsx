import { fireEvent, render, screen, within } from '@testing-library/react'
import type { StrategyInfo } from '@/types/catalog'
import type { WarehouseState } from '@/types/simulation'
import { WarehousePanel } from './WarehousePanel'

const layout = { aisles: 2, positions: 2, levels: 2, locationCapacity: 1000 }

const STRATEGIES: StrategyInfo[] = [
  { id: 'FIRST_FREE', displayName: 'Primera libre', description: 'Llena pasillo por pasillo.' },
  { id: 'LEAST_USED_AISLE', displayName: 'Pasillo menos ocupado', description: 'Reparte entre pasillos.' },
]

const warehouse = (overrides: Partial<WarehouseState> = {}): WarehouseState => ({
  layout,
  strategy: 'FIRST_FREE',
  usedLocations: 3,
  totalLocations: 8,
  wastedVolume: 900,
  rejectedUnits: 0,
  locations: [
    { lotId: 'LOT-A', productId: 'P-007', units: 16, usedVolume: 960 },
    { lotId: 'LOT-B', productId: 'P-001', units: 100, usedVolume: 200 },
    null,
    { lotId: 'LOT-B', productId: 'P-001', units: 50, usedVolume: 100 },
    null,
    null,
    null,
    null,
  ],
  lots: [
    { lotId: 'LOT-A', productId: 'P-007', units: 16, locations: [0], runs: 1 },
    { lotId: 'LOT-B', productId: 'P-001', units: 150, locations: [1, 3], runs: 2 },
  ],
  ...overrides,
})

function setup(overrides: Partial<React.ComponentProps<typeof WarehousePanel>> = {}) {
  const props = {
    warehouse: warehouse(),
    strategies: STRATEGIES,
    productNames: { 'P-007': 'Refrigeradora', 'P-001': 'Camiseta' },
    productTypeIds: { 'P-007': 'HEAVY', 'P-001': 'STANDARD' },
    merchandiseTypeNames: { HEAVY: 'Pesado', STANDARD: 'Estándar' },
    onStrategyChange: vi.fn(),
    ...overrides,
  }
  render(<WarehousePanel {...props} />)
  return props
}

describe('WarehousePanel', () => {
  it('summarizes the occupancy, the wasted space and the lots', () => {
    setup()

    expect(screen.getByText('3 / 8')).toBeInTheDocument()
    expect(screen.getByText('38% del almacén')).toBeInTheDocument()
    expect(screen.getByText('30%')).toBeInTheDocument() // 900 de las 3 ubicaciones ocupadas × 1.000
    expect(screen.getByText('1 dispersos')).toBeInTheDocument()
  })

  it('draws every location of every aisle', () => {
    setup()

    expect(screen.getByText('Pasillo 1')).toBeInTheDocument()
    expect(screen.getByText('Pasillo 2')).toBeInTheDocument()
    expect(screen.getAllByRole('img', { name: /A\d-P\d-N\d/ })).toHaveLength(8)
  })

  it('tells what each location holds', () => {
    setup()

    expect(screen.getByRole('img', { name: 'A1-P1-N1 · Refrigeradora × 16 (lote LOT-A)' })).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'A1-P1-N2 · Camiseta × 100 (lote LOT-B)' })).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'A1-P2-N1 · libre' })).toBeInTheDocument()
  })

  it('shows a legend with the merchandise types', () => {
    setup()

    const legend = screen.getByRole('list', { name: /Leyenda de colores/ })
    expect(within(legend).getByText('Pesado')).toBeInTheDocument()
    expect(within(legend).getByText('Estándar')).toBeInTheDocument()
  })

  it('highlights on the map the locations of the lot under the pointer', () => {
    setup()
    const lotRow = screen.getByRole('row', { name: /^LOT-B/ })

    fireEvent.mouseEnter(lotRow)

    const highlighted = screen.getByRole('img', { name: 'A1-P1-N2 · Camiseta × 100 (lote LOT-B)' })
    const other = screen.getByRole('img', { name: 'A1-P1-N1 · Refrigeradora × 16 (lote LOT-A)' })
    expect(highlighted.className).toMatch(/highlighted/)
    expect(other.className).not.toMatch(/highlighted/)

    fireEvent.mouseLeave(lotRow)
    expect(highlighted.className).not.toMatch(/highlighted/)
  })

  it('reports a change of strategy', () => {
    const { onStrategyChange } = setup()

    fireEvent.change(screen.getByLabelText('Estrategia de ubicación'), { target: { value: 'LEAST_USED_AISLE' } })

    expect(onStrategyChange).toHaveBeenCalledWith('LEAST_USED_AISLE')
  })

  it('shows how many units were rejected for lack of space', () => {
    setup({ warehouse: warehouse({ rejectedUnits: 340 }) })

    expect(screen.getByText('340')).toBeInTheDocument()
  })
})
