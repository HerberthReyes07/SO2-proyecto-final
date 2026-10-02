import { render, screen } from '@testing-library/react'
import { WaitingOrderItem } from './WaitingOrderItem'

const BASE = {
  position: 3,
  orderId: 'ORD-0042',
  customerName: 'Casa Verde',
  serviceLevelLabel: 'Estándar',
  totalItems: 12,
  criterion: 'Vence en 6 min 0 s',
  overdue: false,
}

function renderItem(overrides: Partial<typeof BASE> = {}) {
  return render(
    <ol>
      <WaitingOrderItem {...BASE} {...overrides} />
    </ol>,
  )
}

describe('WaitingOrderItem', () => {
  it('shows its position, who it is and how big it is', () => {
    renderItem()

    expect(screen.getByLabelText('Posición 3')).toHaveTextContent('3')
    expect(screen.getByText('ORD-0042')).toBeInTheDocument()
    expect(screen.getByText('Casa Verde')).toBeInTheDocument()
    expect(screen.getByText('Estándar')).toBeInTheDocument()
  })

  it('shows the value the current policy sorts by', () => {
    renderItem()

    expect(screen.getByText('Vence en 6 min 0 s')).toBeInTheDocument()
  })

  it('flags an overdue order', () => {
    renderItem({ overdue: true })

    expect(screen.getByText('Retrasado')).toBeInTheDocument()
  })

  it('does not flag an order that is still on time', () => {
    renderItem()

    expect(screen.queryByText('Retrasado')).not.toBeInTheDocument()
  })
})
