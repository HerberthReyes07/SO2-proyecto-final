import { describeArrival } from './describeArrival'

describe('describeArrival', () => {
  it('names a single truck', () => {
    expect(describeArrival(['TRK-0001'])).toBe('Camión TRK-0001 registrado')
  })

  it('summarizes several trucks by count and range instead of listing them all', () => {
    const ids = Array.from({ length: 30 }, (_, i) => `TRK-${String(i + 1).padStart(4, '0')}`)

    expect(describeArrival(ids)).toBe('30 camiones registrados (TRK-0001 a TRK-0030)')
  })

  it('handles an empty answer', () => {
    expect(describeArrival([])).toBe('No se registró ningún camión')
  })
})
