import { fireEvent, render, screen } from '@testing-library/react'
import { OrderLineRow } from './OrderLineRow'

const OPTIONS = [
  { value: 'P-001', label: 'Camiseta · Estándar' },
  { value: 'P-007', label: 'Refrigeradora · Pesado' },
]

function setup(overrides: Partial<React.ComponentProps<typeof OrderLineRow>> = {}) {
  const props = {
    line: { key: 0, productId: 'P-001', quantity: 2 },
    position: 1,
    productOptions: OPTIONS,
    removable: true,
    onChange: vi.fn(),
    onRemove: vi.fn(),
    ...overrides,
  }
  render(<OrderLineRow {...props} />)
  return props
}

describe('OrderLineRow', () => {
  it('reports a product change keeping the quantity', () => {
    const { onChange } = setup()

    fireEvent.change(screen.getByLabelText('Producto 1'), { target: { value: 'P-007' } })

    expect(onChange).toHaveBeenCalledWith({ key: 0, productId: 'P-007', quantity: 2 })
  })

  it('reports a quantity change as a number', () => {
    const { onChange } = setup()

    fireEvent.change(screen.getByLabelText('Cantidad 1'), { target: { value: '7' } })

    expect(onChange).toHaveBeenCalledWith({ key: 0, productId: 'P-001', quantity: 7 })
  })

  it('removes the line', () => {
    const { onRemove } = setup()

    fireEvent.click(screen.getByRole('button', { name: 'Quitar línea 1' }))

    expect(onRemove).toHaveBeenCalledTimes(1)
  })

  it('cannot remove the last remaining line', () => {
    setup({ removable: false })

    expect(screen.getByRole('button', { name: 'Quitar línea 1' })).toBeDisabled()
  })
})
