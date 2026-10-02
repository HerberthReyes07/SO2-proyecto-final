import { formatSimulatedDuration } from './formatSimulatedDuration'

describe('formatSimulatedDuration', () => {
  it('shows only seconds under a minute', () => {
    expect(formatSimulatedDuration(0)).toBe('0 s')
    expect(formatSimulatedDuration(45_000)).toBe('45 s')
  })

  it('shows minutes and seconds under an hour', () => {
    expect(formatSimulatedDuration(130_000)).toBe('2 min 10 s')
    expect(formatSimulatedDuration(60_000)).toBe('1 min 0 s')
  })

  it('shows hours and zero-padded minutes from one hour', () => {
    expect(formatSimulatedDuration(3_900_000)).toBe('1 h 05 min')
  })

  it('rounds to the nearest second and never goes negative', () => {
    expect(formatSimulatedDuration(1_499)).toBe('1 s')
    expect(formatSimulatedDuration(-3_000)).toBe('0 s')
  })
})
