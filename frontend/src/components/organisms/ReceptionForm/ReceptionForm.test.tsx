import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { ReceptionForm } from './ReceptionForm'

const PRODUCTS = [
  { value: 'P-001', label: 'Camiseta · Estándar' },
  { value: 'P-007', label: 'Refrigeradora · Pesado' },
]

function setup(onSubmit = vi.fn().mockResolvedValue(true), extra: Partial<React.ComponentProps<typeof ReceptionForm>> = {}) {
  render(<ReceptionForm productOptions={PRODUCTS} onSubmit={onSubmit} pending={false} {...extra} />)
  return onSubmit
}

const submitButton = () => screen.getByRole('button', { name: 'Registrar llegada' })

describe('ReceptionForm', () => {
  it('cannot be submitted until a product is chosen', () => {
    setup()
    expect(submitButton()).toBeDisabled()

    fireEvent.change(screen.getByLabelText('Producto recibido'), { target: { value: 'P-007' } })

    expect(submitButton()).toBeEnabled()
  })

  it('submits the product, units and trucks', async () => {
    const onSubmit = setup()
    fireEvent.change(screen.getByLabelText('Producto recibido'), { target: { value: 'P-007' } })
    fireEvent.change(screen.getByLabelText('Unidades por camión'), { target: { value: '40' } })
    fireEvent.change(screen.getByLabelText('Camiones'), { target: { value: '5' } })

    fireEvent.click(submitButton())

    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith({ productId: 'P-007', quantity: 40, trucks: 5 }))
  })

  it('defaults to one truck of 100 units', async () => {
    const onSubmit = setup()
    fireEvent.change(screen.getByLabelText('Producto recibido'), { target: { value: 'P-001' } })

    fireEvent.click(submitButton())

    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith({ productId: 'P-001', quantity: 100, trucks: 1 }))
  })

  it('rejects out-of-range numbers before sending anything', () => {
    const onSubmit = setup()
    fireEvent.change(screen.getByLabelText('Producto recibido'), { target: { value: 'P-001' } })
    fireEvent.change(screen.getByLabelText('Unidades por camión'), { target: { value: '0' } })

    expect(submitButton()).toBeDisabled()
    expect(screen.getByText(/Las unidades por camión deben ser/)).toBeInTheDocument()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('shows server errors and confirmations', () => {
    setup(undefined, { error: 'Too many trucks pending (limit 100)', confirmation: 'Camiones TRK-0001 registrados' })

    expect(screen.getByRole('alert')).toHaveTextContent('Too many trucks pending')
    expect(screen.getByRole('status')).toHaveTextContent('TRK-0001')
  })

  it('disables the button while the request is in flight', () => {
    setup(undefined, { pending: true })

    expect(screen.getByRole('button', { name: 'Registrando…' })).toBeDisabled()
  })
})
