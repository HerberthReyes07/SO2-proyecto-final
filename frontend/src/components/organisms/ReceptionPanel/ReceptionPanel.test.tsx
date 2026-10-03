import { render, screen } from '@testing-library/react'
import type { InventoryState, ReceptionState } from '@/types/simulation'
import { ReceptionPanel } from './ReceptionPanel'

const NAMES = { 'P-001': 'Camiseta', 'P-007': 'Refrigeradora' }

const inventory = (overrides: Partial<InventoryState> = {}): InventoryState => ({
  stock: [],
  buffer: { capacity: 10, size: 2, blockedProducers: 0, accepted: 5, delivered: 3 },
  worker: { processing: null, processed: 3 },
  ...overrides,
})

const noTrucks: ReceptionState = { trucks: [] }

describe('ReceptionPanel', () => {
  it('shows the buffer that connects both areas', () => {
    render(<ReceptionPanel reception={noTrucks} inventory={inventory()} productNames={NAMES} />)

    expect(screen.getByText('Buffer Recepción → Inventario')).toBeInTheDocument()
    expect(screen.getByText('2 / 10 mensajes · 5 recibidos · 3 procesados')).toBeInTheDocument()
  })

  it('says inventory is idle when it processes nothing', () => {
    render(<ReceptionPanel reception={noTrucks} inventory={inventory()} productNames={NAMES} />)

    expect(screen.getByText('Inventario está ocioso.')).toBeInTheDocument()
  })

  it('says what inventory is processing right now', () => {
    const busy = inventory({ worker: { processing: { truckId: 'TRK-0002', productId: 'P-007', quantity: 40 }, processed: 1 } })

    render(<ReceptionPanel reception={noTrucks} inventory={busy} productNames={NAMES} />)

    expect(screen.getByText('Inventario procesa TRK-0002: Refrigeradora × 40')).toBeInTheDocument()
  })

  it('invites to register an arrival when there are no trucks', () => {
    render(<ReceptionPanel reception={noTrucks} inventory={inventory()} productNames={NAMES} />)

    expect(screen.getByText(/No hay camiones en recepción/)).toBeInTheDocument()
  })

  it('lists the trucks in reception with the product name', () => {
    const reception: ReceptionState = {
      trucks: [
        { truckId: 'TRK-0001', productId: 'P-001', quantity: 10, phase: 'UNLOADING', awaiting: null, held: ['SCANNER'], progress: 0.5 },
        { truckId: 'TRK-0002', productId: 'P-007', quantity: 20, phase: 'REPORTING', awaiting: null, held: [], progress: 1 },
      ],
    }

    render(<ReceptionPanel reception={reception} inventory={inventory()} productNames={NAMES} />)

    expect(screen.getAllByRole('listitem')).toHaveLength(2)
    expect(screen.getByText('Camiseta × 10')).toBeInTheDocument()
    expect(screen.getByText('Refrigeradora × 20')).toBeInTheDocument()
  })

  it('shows the backpressure warning when reception is faster than inventory', () => {
    const congested = inventory({
      buffer: { capacity: 10, size: 10, blockedProducers: 4, accepted: 14, delivered: 4 },
    })

    render(<ReceptionPanel reception={noTrucks} inventory={congested} productNames={NAMES} />)

    expect(screen.getByText('Contrapresión: 4 esperando espacio')).toBeInTheDocument()
  })
})
