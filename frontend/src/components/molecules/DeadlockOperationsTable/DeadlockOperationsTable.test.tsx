import { render, screen, within } from '@testing-library/react'
import type { DeadlockedOperation } from '@/types/simulation'
import { DeadlockOperationsTable } from './DeadlockOperationsTable'

const OPERATIONS: DeadlockedOperation[] = [
  { id: 'ORD-0001', kind: 'ORDER', held: ['WAREHOUSE_WORKER', 'SCANNER'], waitingFor: 'LOADING_DOCK' },
  { id: 'TRK-0001', kind: 'TRUCK', held: ['LOADING_DOCK'], waitingFor: 'SCANNER' },
]

const rowOf = (id: string) => screen.getByRole('row', { name: new RegExp(`^${id}`) })

describe('DeadlockOperationsTable', () => {
  it('shows each operation, its kind, what it holds and what it waits for', () => {
    render(<DeadlockOperationsTable operations={OPERATIONS} contested={['SCANNER', 'LOADING_DOCK']} />)

    const order = within(rowOf('ORD-0001'))
    expect(order.getByText('Pedido')).toBeInTheDocument()
    expect(order.getByText('Encargados de bodega')).toBeInTheDocument()
    expect(order.getByText('Sistema de escaneo')).toBeInTheDocument()
    expect(order.getByText('Áreas de carga')).toBeInTheDocument() // lo que espera

    const truck = within(rowOf('TRK-0001'))
    expect(truck.getByText('Camión')).toBeInTheDocument()
  })

  it('highlights the contested resources among the ones an operation holds', () => {
    render(<DeadlockOperationsTable operations={OPERATIONS} contested={['SCANNER', 'LOADING_DOCK']} />)

    const order = within(rowOf('ORD-0001'))
    expect(order.getByText('Sistema de escaneo').className).toMatch(/danger/)
    expect(order.getByText('Encargados de bodega').className).not.toMatch(/danger/) // lo retiene, pero nadie lo disputa
  })
})
