import { fireEvent, render, screen, within } from '@testing-library/react'
import type { PolicyInfo } from '@/types/catalog'
import type { WaitingOrder } from '@/types/simulation'
import { WaitingListPanel } from './WaitingListPanel'

const MINUTE = 60_000

const POLICIES: PolicyInfo[] = [
  { id: 'STRICT_PRIORITY', displayName: 'Prioridad estricta', description: 'Gana el nivel más urgente.' },
  { id: 'EARLIEST_DEADLINE_FIRST', displayName: 'EDF', description: 'Gana el vencimiento más próximo.' },
]

const waiting = (orderId: string, waitedMinutes: number, serviceLevel: WaitingOrder['serviceLevel'] = 'PRIORITY'): WaitingOrder => ({
  orderId,
  customerName: 'Casa Verde',
  serviceLevel,
  totalItems: 5,
  waitedMillis: waitedMinutes * MINUTE,
  targetWaitMillis: 10 * MINUTE,
})

function setup(overrides: Partial<React.ComponentProps<typeof WaitingListPanel>> = {}) {
  const props = {
    policies: POLICIES,
    currentPolicy: 'STRICT_PRIORITY' as const,
    waiting: [waiting('ORD-0001', 2), waiting('ORD-0002', 12)],
    waitingTotal: 2,
    serviceLevelNames: { PRIORITY: 'Prioritario', ECONOMY: 'Económico' },
    onPolicyChange: vi.fn(),
    ...overrides,
  }
  render(<WaitingListPanel {...props} />)
  return props
}

describe('WaitingListPanel', () => {
  it('lists the orders in the order received, numbered from 1', () => {
    setup()

    const items = screen.getAllByRole('listitem')
    expect(items).toHaveLength(2)
    expect(within(items[0]).getByText('ORD-0001')).toBeInTheDocument()
    expect(within(items[0]).getByLabelText('Posición 1')).toBeInTheDocument()
    expect(within(items[1]).getByText('ORD-0002')).toBeInTheDocument()
  })

  it('shows how many orders are waiting', () => {
    setup({ waitingTotal: 2 })

    expect(screen.getByText('2 en espera')).toBeInTheDocument()
  })

  it('shows the criterion of the current policy next to each order', () => {
    setup({ currentPolicy: 'EARLIEST_DEADLINE_FIRST' })

    expect(screen.getByText('Vence en 8 min 0 s')).toBeInTheDocument()
    expect(screen.getByText('Venció hace 2 min 0 s')).toBeInTheDocument()
  })

  it('flags only the orders that exceeded their target wait', () => {
    setup()

    expect(screen.getAllByText('Retrasado')).toHaveLength(1)
  })

  it('says how many orders are not shown when the list is truncated', () => {
    setup({ waitingTotal: 130 })

    expect(screen.getByText('… y 128 pedidos más en espera')).toBeInTheDocument()
  })

  it('reports a policy change', () => {
    const { onPolicyChange } = setup()

    fireEvent.change(screen.getByLabelText('Política de atención'), { target: { value: 'EARLIEST_DEADLINE_FIRST' } })

    expect(onPolicyChange).toHaveBeenCalledWith('EARLIEST_DEADLINE_FIRST')
  })

  it('shows an empty state when nobody is waiting', () => {
    setup({ waiting: [], waitingTotal: 0 })

    expect(screen.getByText('No hay pedidos esperando.')).toBeInTheDocument()
    expect(screen.getByText('0 en espera')).toBeInTheDocument()
  })
})
