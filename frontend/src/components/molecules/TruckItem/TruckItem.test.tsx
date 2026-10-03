import { render, screen } from '@testing-library/react'
import type { Truck } from '@/types/simulation'
import { TruckItem } from './TruckItem'

const UNLOADING: Truck = {
  truckId: 'TRK-0003',
  productId: 'P-007',
  quantity: 40,
  phase: 'UNLOADING',
  awaiting: null,
  held: ['SCANNER', 'LOADING_DOCK'],
  progress: 0.25,
  retries: 0,
}

function renderTruck(truck: Truck) {
  return render(
    <ul>
      <TruckItem truck={truck} productName="Refrigeradora" />
    </ul>,
  )
}

describe('TruckItem', () => {
  it('shows the truck, what it brings and its phase', () => {
    renderTruck(UNLOADING)

    expect(screen.getByText('TRK-0003')).toBeInTheDocument()
    expect(screen.getByText('Refrigeradora × 40')).toBeInTheDocument()
    expect(screen.getByText('Descargando')).toBeInTheDocument()
    expect(screen.getByRole('progressbar', { name: 'Descarga de TRK-0003' })).toHaveAttribute('aria-valuenow', '25')
  })

  it('lists the resources it holds', () => {
    renderTruck(UNLOADING)

    expect(screen.getByText('Sistema de escaneo')).toBeInTheDocument()
    expect(screen.getByText('Áreas de carga')).toBeInTheDocument()
  })

  it('highlights the resource it is waiting for', () => {
    renderTruck({ ...UNLOADING, phase: 'WAITING_RESOURCES', awaiting: 'SCANNER', held: [], progress: 0 })

    expect(screen.getByText('Esperando: Sistema de escaneo')).toBeInTheDocument()
    expect(screen.getByText('Esperando escáner o andén')).toBeInTheDocument()
  })

  it('shows a truck that was expelled to undo a deadlock and is waiting to retry', () => {
    renderTruck({ ...UNLOADING, phase: 'RETRYING', held: [], progress: 0, retries: 1 })

    expect(screen.getByText('Expulsado: esperando para reintentar')).toBeInTheDocument()
    expect(screen.getByText('Reintento 1')).toBeInTheDocument()
  })

  it('shows that a truck that already released its resources is only reporting to inventory', () => {
    renderTruck({ ...UNLOADING, phase: 'REPORTING', held: [], progress: 1 })

    expect(screen.getByText('Informando a Inventario')).toBeInTheDocument()
    expect(screen.queryByText('Sistema de escaneo')).not.toBeInTheDocument()
  })
})
