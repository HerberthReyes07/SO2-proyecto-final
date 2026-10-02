import { MAX_ORDER_LINES, MAX_QUANTITY_PER_LINE } from '@/constants/orderLimits'
import { createEmptyLine, toPlaceOrderRequest, totalUnits, validateDraft, type OrderLineDraft } from './orderDraft'

const line = (key: number, productId: string, quantity: number): OrderLineDraft => ({ key, productId, quantity })

describe('validateDraft', () => {
  it('accepts a complete draft', () => {
    expect(validateDraft('CUS-001', [line(0, 'P-001', 3)])).toBeNull()
  })

  it('requires a customer', () => {
    expect(validateDraft('', [line(0, 'P-001', 1)])).toMatch(/cliente/i)
  })

  it('requires a product in every line', () => {
    expect(validateDraft('CUS-001', [line(0, 'P-001', 1), createEmptyLine(1)])).toMatch(/producto/i)
  })

  it('requires at least one line', () => {
    expect(validateDraft('CUS-001', [])).toMatch(/al menos un producto/i)
  })

  it.each([0, -1, 1.5, Number.NaN, MAX_QUANTITY_PER_LINE + 1])('rejects quantity %s', (quantity) => {
    expect(validateDraft('CUS-001', [line(0, 'P-001', quantity)])).toMatch(/cantidad/i)
  })

  it('rejects more lines than the limit', () => {
    const lines = Array.from({ length: MAX_ORDER_LINES + 1 }, (_, index) => line(index, `P-${index}`, 1))
    expect(validateDraft('CUS-001', lines)).toMatch(/como máximo/i)
  })
})

describe('totalUnits', () => {
  it('adds the quantity of every line and ignores invalid values', () => {
    expect(totalUnits([line(0, 'P-001', 2), line(1, 'P-004', 3), line(2, 'P-007', Number.NaN)])).toBe(5)
  })
})

describe('toPlaceOrderRequest', () => {
  it('drops the UI-only key', () => {
    expect(toPlaceOrderRequest('CUS-001', [line(7, 'P-001', 2)])).toEqual({
      customerId: 'CUS-001',
      lines: [{ productId: 'P-001', quantity: 2 }],
    })
  })
})
