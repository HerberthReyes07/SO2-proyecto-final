import { MAX_QUANTITY_PER_TRUCK, MAX_TRUCKS_PER_REQUEST } from '@/constants/receptionLimits'
import { validateArrival } from './arrivalDraft'

describe('validateArrival', () => {
  it('accepts a complete arrival', () => {
    expect(validateArrival('P-001', 100, 1)).toBeNull()
    expect(validateArrival('P-001', MAX_QUANTITY_PER_TRUCK, MAX_TRUCKS_PER_REQUEST)).toBeNull()
  })

  it('requires a product', () => {
    expect(validateArrival('', 100, 1)).toMatch(/producto/i)
  })

  it.each([0, -5, 1.5, Number.NaN, MAX_QUANTITY_PER_TRUCK + 1])('rejects %s units per truck', (quantity) => {
    expect(validateArrival('P-001', quantity, 1)).toMatch(/unidades/i)
  })

  it.each([0, -1, 2.5, Number.NaN, MAX_TRUCKS_PER_REQUEST + 1])('rejects %s trucks', (trucks) => {
    expect(validateArrival('P-001', 100, trucks)).toMatch(/camiones/i)
  })
})
