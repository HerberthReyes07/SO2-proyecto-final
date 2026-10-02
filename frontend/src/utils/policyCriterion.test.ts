import type { WaitingOrder } from '@/types/simulation'
import { describeCriterion, isOverdue } from './policyCriterion'

const MINUTE = 60_000

const order = (overrides: Partial<WaitingOrder> = {}): WaitingOrder => ({
  orderId: 'ORD-0001',
  customerName: 'Casa Verde',
  serviceLevel: 'PRIORITY',
  totalItems: 12,
  waitedMillis: 4 * MINUTE,
  targetWaitMillis: 10 * MINUTE,
  ...overrides,
})

describe('isOverdue', () => {
  it('is false while the order waits less than its target', () => {
    expect(isOverdue(order())).toBe(false)
  })

  it('is false exactly at the target and true once it is exceeded', () => {
    expect(isOverdue(order({ waitedMillis: 10 * MINUTE }))).toBe(false)
    expect(isOverdue(order({ waitedMillis: 10 * MINUTE + 1 }))).toBe(true)
  })
})

describe('describeCriterion', () => {
  it('shows arrival time for FIFO', () => {
    expect(describeCriterion('FIFO', order(), 'Prioritario')).toBe('Llegó hace 4 min 0 s')
  })

  it('shows the service level for strict priority', () => {
    expect(describeCriterion('STRICT_PRIORITY', order(), 'Prioritario')).toBe('Nivel Prioritario')
  })

  it('shows both level and waited time for aging', () => {
    expect(describeCriterion('AGING_PRIORITY', order(), 'Prioritario')).toBe('Nivel Prioritario · esperó 4 min 0 s')
  })

  it('shows the time left before the deadline for EDF', () => {
    expect(describeCriterion('EARLIEST_DEADLINE_FIRST', order(), 'Prioritario')).toBe('Vence en 6 min 0 s')
  })

  it('shows how long ago the deadline passed for an overdue order under EDF', () => {
    const overdue = order({ waitedMillis: 15 * MINUTE })

    expect(describeCriterion('EARLIEST_DEADLINE_FIRST', overdue, 'Prioritario')).toBe('Venció hace 5 min 0 s')
  })

  it('shows the size for SJF', () => {
    expect(describeCriterion('SHORTEST_JOB_FIRST', order(), 'Prioritario')).toBe('12 artículos')
  })
})
