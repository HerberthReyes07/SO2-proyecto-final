import { LOW_STOCK_UNITS } from '@/constants/stockThresholds'
import { describeMissingStock, getStockStatus } from './stockStatus'

describe('getStockStatus', () => {
  it('flags an exhausted product', () => {
    expect(getStockStatus(0)).toEqual({ label: 'Agotado', tone: 'danger' })
  })

  it('flags a product running low', () => {
    expect(getStockStatus(LOW_STOCK_UNITS - 1)).toEqual({ label: 'Bajo', tone: 'warning' })
  })

  it('says nothing when there is plenty', () => {
    expect(getStockStatus(LOW_STOCK_UNITS)).toBeNull()
    expect(getStockStatus(300)).toBeNull()
  })
})

describe('describeMissingStock', () => {
  const names = { 'P-001': 'Camiseta', 'P-007': 'Refrigeradora' }

  it('lists each missing product with its shortfall', () => {
    expect(
      describeMissingStock(
        [
          { productId: 'P-001', missingUnits: 5 },
          { productId: 'P-007', missingUnits: 2 },
        ],
        names,
      ),
    ).toBe('Camiseta (faltan 5), Refrigeradora (faltan 2)')
  })

  it('falls back to the product id when its name is unknown', () => {
    expect(describeMissingStock([{ productId: 'P-999', missingUnits: 1 }], names)).toBe('P-999 (faltan 1)')
  })
})
