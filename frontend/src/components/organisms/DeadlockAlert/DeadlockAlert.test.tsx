import { fireEvent, render, screen } from '@testing-library/react'
import type { DeadlockReport } from '@/types/simulation'
import { DeadlockAlert } from './DeadlockAlert'

const REPORT: DeadlockReport = {
  id: 'DL-0001',
  detectedAtMillis: 0,
  operations: [
    { id: 'ORD-0001', kind: 'ORDER', held: ['WAREHOUSE_WORKER', 'SCANNER'], waitingFor: 'LOADING_DOCK' },
    { id: 'TRK-0001', kind: 'TRUCK', held: ['LOADING_DOCK'], waitingFor: 'SCANNER' },
    { id: 'TRK-0002', kind: 'TRUCK', held: ['LOADING_DOCK'], waitingFor: 'SCANNER' },
  ],
  resources: ['SCANNER', 'LOADING_DOCK'],
  conflict: { waiter: 'ORD-0001', holder: 'TRK-0001' },
}

describe('DeadlockAlert', () => {
  it('names the deadlock and explains why the two operations cannot move', () => {
    render(<DeadlockAlert report={REPORT} onResolve={() => {}} pending={false} />)

    expect(screen.getByRole('heading', { name: 'Deadlock detectado (DL-0001)' })).toBeInTheDocument()
    expect(screen.getAllByText(/ORD-0001 espera Áreas de carga, que retiene TRK-0001\./).length).toBeGreaterThan(0)
    expect(screen.getByText(/Se esperan mutuamente/)).toBeInTheDocument()
  })

  it('lists every blocked operation and the contested resources', () => {
    render(<DeadlockAlert report={REPORT} onResolve={() => {}} pending={false} />)

    expect(screen.getAllByRole('row')).toHaveLength(4) // encabezado + 3 operaciones
    expect(screen.getByText(/Recursos disputados/)).toBeInTheDocument()
  })

  it('shows the four Coffman conditions as fulfilled', () => {
    render(<DeadlockAlert report={REPORT} onResolve={() => {}} pending={false} />)

    for (const name of ['Exclusión mutua', 'Retención y espera', 'Sin apropiación', 'Espera circular']) {
      expect(screen.getByText(name)).toBeInTheDocument()
    }
    expect(screen.getAllByLabelText('Se cumple')).toHaveLength(4)
  })

  it('lets the user choose which of the two operations in conflict continues', () => {
    const onResolve = vi.fn()
    render(<DeadlockAlert report={REPORT} onResolve={onResolve} pending={false} />)

    fireEvent.click(screen.getByRole('button', { name: 'Que continúe ORD-0001' }))
    fireEvent.click(screen.getByRole('button', { name: 'Que continúe TRK-0001' }))

    expect(onResolve).toHaveBeenNthCalledWith(1, 'ORD-0001')
    expect(onResolve).toHaveBeenNthCalledWith(2, 'TRK-0001')
  })

  it('does not offer the operation outside the conflict as a choice', () => {
    render(<DeadlockAlert report={REPORT} onResolve={() => {}} pending={false} />)

    expect(screen.queryByRole('button', { name: 'Que continúe TRK-0002' })).not.toBeInTheDocument()
  })

  it('disables the choices while a resolution is in progress', () => {
    render(<DeadlockAlert report={REPORT} onResolve={() => {}} pending />)

    expect(screen.getByRole('button', { name: 'Que continúe ORD-0001' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Que continúe TRK-0001' })).toBeDisabled()
  })
})
