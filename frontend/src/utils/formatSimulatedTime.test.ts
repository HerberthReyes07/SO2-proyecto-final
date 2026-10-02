import { formatSimulatedTime } from './formatSimulatedTime'

describe('formatSimulatedTime', () => {
  it('starts at day 1, 00:00:00', () => {
    expect(formatSimulatedTime(0)).toBe('Día 1 · 00:00:00')
  })

  it('formats hours, minutes and seconds', () => {
    const millis = ((2 * 60 + 5) * 60 + 9) * 1000
    expect(formatSimulatedTime(millis)).toBe('Día 1 · 02:05:09')
  })

  it('rolls over to the next day after 24 simulated hours', () => {
    expect(formatSimulatedTime(24 * 3600 * 1000)).toBe('Día 2 · 00:00:00')
    expect(formatSimulatedTime((48 * 3600 + 61) * 1000)).toBe('Día 3 · 00:01:01')
  })

  it('ignores sub-second precision and clamps negative values to zero', () => {
    expect(formatSimulatedTime(999)).toBe('Día 1 · 00:00:00')
    expect(formatSimulatedTime(-5000)).toBe('Día 1 · 00:00:00')
  })
})
