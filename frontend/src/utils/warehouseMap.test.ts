import type { WarehouseState } from '@/types/simulation'
import { buildAisles, typeColor } from './warehouseMap'

// 2 pasillos × 2 posiciones × 2 niveles = 8 ubicaciones (4 por pasillo).
const layout = { aisles: 2, positions: 2, levels: 2, locationCapacity: 1000 }

const warehouse = (overrides: Partial<WarehouseState> = {}): WarehouseState => ({
  layout,
  strategy: 'FIRST_FREE',
  usedLocations: 2,
  totalLocations: 8,
  wastedVolume: 0,
  rejectedUnits: 0,
  locations: [
    { lotId: 'LOT-A', productId: 'P-007', units: 10, usedVolume: 600 },
    null,
    null,
    null,
    { lotId: 'LOT-B', productId: 'P-001', units: 100, usedVolume: 200 },
    null,
    null,
    null,
  ],
  lots: [],
  ...overrides,
})

const lookup = {
  productNames: { 'P-007': 'Refrigeradora', 'P-001': 'Camiseta' },
  productTypeIds: { 'P-007': 'HEAVY', 'P-001': 'STANDARD' },
}

describe('typeColor', () => {
  it('points at the design token of the merchandise type', () => {
    expect(typeColor('HEAVY')).toBe('var(--color-type-heavy)')
    expect(typeColor('PERISHABLE')).toBe('var(--color-type-perishable)')
  })

  it('uses a neutral color when the type is unknown', () => {
    expect(typeColor(undefined)).toBe('var(--color-neutral)')
  })
})

describe('buildAisles', () => {
  it('builds one list of cells per aisle with every location of the aisle', () => {
    const aisles = buildAisles(warehouse(), lookup, null)

    expect(aisles).toHaveLength(2)
    expect(aisles[0].map((cell) => cell.index)).toEqual([0, 1, 2, 3])
    expect(aisles[1].map((cell) => cell.index)).toEqual([4, 5, 6, 7])
  })

  it('describes a free location by its code', () => {
    const [aisle] = buildAisles(warehouse(), lookup, null)

    expect(aisle[1]).toMatchObject({ label: 'A1-P1-N2 · libre', fillRatio: 0 })
    expect(aisle[1].color).toBeUndefined()
  })

  it('describes an occupied location with its product, units, lot, fill and type color', () => {
    const [aisle] = buildAisles(warehouse(), lookup, null)

    expect(aisle[0]).toMatchObject({
      label: 'A1-P1-N1 · Refrigeradora × 10 (lote LOT-A)',
      fillRatio: 0.6,
      color: 'var(--color-type-heavy)',
      highlighted: false,
    })
  })

  it('puts the second aisle at the right coordinates', () => {
    const aisles = buildAisles(warehouse(), lookup, null)

    expect(aisles[1][0].label).toBe('A2-P1-N1 · Camiseta × 100 (lote LOT-B)')
  })

  it('highlights only the locations of the chosen lot', () => {
    const aisles = buildAisles(warehouse(), lookup, 'LOT-B')

    expect(aisles[0][0].highlighted).toBe(false)
    expect(aisles[1][0].highlighted).toBe(true)
  })

  it('falls back to ids when the catalog does not know the product', () => {
    const [aisle] = buildAisles(warehouse(), { productNames: {}, productTypeIds: {} }, null)

    expect(aisle[0].label).toBe('A1-P1-N1 · P-007 × 10 (lote LOT-A)')
    expect(aisle[0].color).toBe('var(--color-neutral)')
  })
})
