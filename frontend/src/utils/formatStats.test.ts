import { formatPercent, formatThroughput, parseBoundedInteger, parseSeed } from './formatStats'

describe('formatPercent', () => {
  it('rounds a ratio to a whole percentage', () => {
    expect(formatPercent(0)).toBe('0%')
    expect(formatPercent(0.734)).toBe('73%')
    expect(formatPercent(1)).toBe('100%')
  })
})

describe('formatThroughput', () => {
  it('shows one decimal with a comma', () => {
    expect(formatThroughput(1.46)).toBe('1,5')
    expect(formatThroughput(0)).toBe('0,0')
  })
})

describe('parseSeed', () => {
  it('accepts whole numbers, ignoring surrounding spaces', () => {
    expect(parseSeed('42')).toBe(42)
    expect(parseSeed('  7 ')).toBe(7)
  })

  it.each(['', '   ', 'abc', '1.5', '12a', '1e3'])('rejects %j', (text) => {
    expect(parseSeed(text)).toBeNull()
  })

  it('rejects integers too large to be represented exactly', () => {
    expect(parseSeed('9007199254740993')).toBeNull()
  })
})

describe('parseBoundedInteger', () => {
  it.each([
    ['50', 50],
    [' 10 ', 10],
    ['500', 500],
  ])('accepts %j', (text, expected) => {
    expect(parseBoundedInteger(text, 10, 500)).toBe(expected)
  })

  it.each(['', '9', '501', '12.5', '-20', 'abc', '1e2'])('rejects %j', (text) => {
    expect(parseBoundedInteger(text, 10, 500)).toBeNull()
  })
})
