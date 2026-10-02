import { render, screen } from '@testing-library/react'
import type { OrderProgress } from '@/types/simulation'
import { ActiveOrdersPanel } from './ActiveOrdersPanel'

const order = (orderId: string): OrderProgress => ({
  orderId,
  customerName: 'Casa Verde',
  serviceLevel: 'STANDARD',
  totalItems: 3,
  status: 'PROCESSING',
  stage: 'PICKING',
  awaiting: null,
  held: ['WAREHOUSE_WORKER'],
  progress: 0.1,
  remainingMillis: 60_000,
})

describe('ActiveOrdersPanel', () => {
  it('invites to register an order when there are none', () => {
    render(<ActiveOrdersPanel orders={[]} completedCount={0} serviceLevelNames={{}} />)

    expect(screen.getByText(/No hay pedidos en proceso/)).toBeInTheDocument()
  })

  it('lists every order with its service level name', () => {
    render(
      <ActiveOrdersPanel
        orders={[order('ORD-0001'), order('ORD-0002')]}
        completedCount={4}
        serviceLevelNames={{ STANDARD: 'Estándar' }}
      />,
    )

    expect(screen.getAllByRole('listitem')).toHaveLength(2)
    expect(screen.getAllByText('Estándar')).toHaveLength(2)
  })

  it('shows how many orders have been completed', () => {
    render(<ActiveOrdersPanel orders={[]} completedCount={12} serviceLevelNames={{}} />)

    expect(screen.getByText('Completados: 12')).toBeInTheDocument()
  })
})
