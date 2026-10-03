import { render, screen } from '@testing-library/react'
import type { OrderProgress } from '@/types/simulation'
import { ActiveOrderItem } from './ActiveOrderItem'

const WORKING: OrderProgress = {
  orderId: 'ORD-0007',
  customerName: 'Casa Verde',
  serviceLevel: 'STANDARD',
  totalItems: 12,
  status: 'PROCESSING',
  stage: 'PACKING',
  awaiting: null,
  held: ['WAREHOUSE_WORKER', 'PACKING_STATION'],
  progress: 0.4,
  remainingMillis: 130_000,
  retries: 0,
}

function renderItem(order: OrderProgress) {
  return render(
    <ul>
      <ActiveOrderItem order={order} serviceLevelLabel="Estándar" />
    </ul>,
  )
}

describe('ActiveOrderItem', () => {
  it('shows who the order is, its stage and size', () => {
    renderItem(WORKING)

    expect(screen.getByText('ORD-0007')).toBeInTheDocument()
    expect(screen.getByText('Casa Verde')).toBeInTheDocument()
    expect(screen.getByText('Estándar')).toBeInTheDocument()
    expect(screen.getByText('12 artículos · Empaque')).toBeInTheDocument()
  })

  it('shows progress and remaining simulated time', () => {
    renderItem(WORKING)

    expect(screen.getByRole('progressbar', { name: 'Avance de ORD-0007' })).toHaveAttribute('aria-valuenow', '40')
    expect(screen.getByText('40% · restante ≈ 2 min 10 s simulados')).toBeInTheDocument()
  })

  it('lists the resources it holds', () => {
    renderItem(WORKING)

    expect(screen.getByText('Encargados de bodega')).toBeInTheDocument()
    expect(screen.getByText('Estaciones de empaque')).toBeInTheDocument()
    expect(screen.queryByText(/Esperando/)).not.toBeInTheDocument()
  })

  it('shows how many times it was expelled to undo a deadlock', () => {
    renderItem({ ...WORKING, retries: 2 })

    expect(screen.getByText('Reintento 2')).toBeInTheDocument()
  })

  it('shows no retry badge for an order that never had to retry', () => {
    renderItem(WORKING)

    expect(screen.queryByText(/Reintento/)).not.toBeInTheDocument()
  })

  it('highlights the resource it is waiting for', () => {
    renderItem({ ...WORKING, awaiting: 'FORKLIFT', held: ['WAREHOUSE_WORKER'] })

    expect(screen.getByText('Esperando: Montacargas')).toBeInTheDocument()
  })
})
