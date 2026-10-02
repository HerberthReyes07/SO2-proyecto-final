import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MAX_ORDER_LINES } from '@/constants/orderLimits'
import { OrderForm } from './OrderForm'

const CUSTOMERS = [{ value: 'CUS-001', label: 'Aurora Tech · Exprés' }]
const PRODUCTS = [
  { value: 'P-001', label: 'Camiseta · Estándar' },
  { value: 'P-007', label: 'Refrigeradora · Pesado' },
]

function setup(onSubmit = vi.fn().mockResolvedValue(true), extra: Partial<React.ComponentProps<typeof OrderForm>> = {}) {
  render(
    <OrderForm customerOptions={CUSTOMERS} productOptions={PRODUCTS} onSubmit={onSubmit} pending={false} {...extra} />,
  )
  return onSubmit
}

const choose = (label: string, value: string) =>
  fireEvent.change(screen.getByLabelText(label), { target: { value } })
const submitButton = () => screen.getByRole('button', { name: 'Registrar pedido' })

describe('OrderForm', () => {
  it('starts with one empty line and cannot be submitted', () => {
    setup()

    expect(screen.getAllByLabelText(/^Producto \d+$/)).toHaveLength(1)
    expect(submitButton()).toBeDisabled()
    expect(screen.getByText('Seleccione un cliente.')).toBeInTheDocument()
  })

  it('becomes submittable once customer and product are chosen', () => {
    setup()

    choose('Cliente', 'CUS-001')
    choose('Producto 1', 'P-001')

    expect(submitButton()).toBeEnabled()
  })

  it('submits every line and the customer', async () => {
    const onSubmit = setup()
    choose('Cliente', 'CUS-001')
    choose('Producto 1', 'P-001')
    choose('Cantidad 1', '3')
    fireEvent.click(screen.getByRole('button', { name: '+ Agregar producto' }))
    choose('Producto 2', 'P-007')
    choose('Cantidad 2', '2')

    fireEvent.click(submitButton())

    await waitFor(() =>
      expect(onSubmit).toHaveBeenCalledWith({
        customerId: 'CUS-001',
        lines: [
          { productId: 'P-001', quantity: 3 },
          { productId: 'P-007', quantity: 2 },
        ],
      }),
    )
  })

  it('shows the running total of items', () => {
    setup()
    choose('Producto 1', 'P-001')
    choose('Cantidad 1', '4')
    fireEvent.click(screen.getByRole('button', { name: '+ Agregar producto' }))
    choose('Cantidad 2', '6')

    expect(screen.getByText('Total: 10 artículos')).toBeInTheDocument()
  })

  it('removes a line but never the last one', () => {
    setup()
    fireEvent.click(screen.getByRole('button', { name: '+ Agregar producto' }))
    expect(screen.getAllByLabelText(/^Producto \d+$/)).toHaveLength(2)

    fireEvent.click(screen.getByRole('button', { name: 'Quitar línea 2' }))

    expect(screen.getAllByLabelText(/^Producto \d+$/)).toHaveLength(1)
    expect(screen.getByRole('button', { name: 'Quitar línea 1' })).toBeDisabled()
  })

  it('stops offering more lines at the limit', () => {
    setup()
    for (let i = 1; i < MAX_ORDER_LINES; i++) {
      fireEvent.click(screen.getByRole('button', { name: '+ Agregar producto' }))
    }

    expect(screen.getAllByLabelText(/^Producto \d+$/)).toHaveLength(MAX_ORDER_LINES)
    expect(screen.getByRole('button', { name: '+ Agregar producto' })).toBeDisabled()
  })

  it('clears the lines but keeps the customer after an accepted order', async () => {
    setup()
    choose('Cliente', 'CUS-001')
    choose('Producto 1', 'P-001')

    fireEvent.click(submitButton())

    await waitFor(() => expect(screen.getByLabelText('Producto 1')).toHaveDisplayValue('Seleccione un producto'))
    expect(screen.getByLabelText('Cliente')).toHaveValue('CUS-001')
  })

  it('keeps the draft when the server rejects the order', async () => {
    const onSubmit = setup(vi.fn().mockResolvedValue(false))
    choose('Cliente', 'CUS-001')
    choose('Producto 1', 'P-007')

    fireEvent.click(submitButton())

    await waitFor(() => expect(onSubmit).toHaveBeenCalled())
    expect(screen.getByLabelText('Producto 1')).toHaveValue('P-007')
  })

  it('rejects an out-of-range quantity before sending anything', () => {
    const onSubmit = setup()
    choose('Cliente', 'CUS-001')
    choose('Producto 1', 'P-001')
    choose('Cantidad 1', '0')

    expect(submitButton()).toBeDisabled()
    expect(screen.getByText(/La cantidad debe ser/)).toBeInTheDocument()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('shows server errors and confirmations', () => {
    setup(undefined, { error: 'Unknown customer: CUS-999', confirmation: 'Pedido ORD-0001 registrado' })

    expect(screen.getByRole('alert')).toHaveTextContent('Unknown customer: CUS-999')
    expect(screen.getByRole('status')).toHaveTextContent('Pedido ORD-0001 registrado')
  })
})
