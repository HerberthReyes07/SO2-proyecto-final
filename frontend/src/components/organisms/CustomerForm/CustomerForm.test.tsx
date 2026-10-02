import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { CustomerForm } from './CustomerForm'

const LEVELS = [
  { value: 'EXPRESS', label: 'Nivel 1 · Exprés' },
  { value: 'ECONOMY', label: 'Nivel 5 · Económico' },
]

function setup(onSubmit = vi.fn().mockResolvedValue(true), extra: Partial<React.ComponentProps<typeof CustomerForm>> = {}) {
  render(<CustomerForm serviceLevelOptions={LEVELS} onSubmit={onSubmit} pending={false} {...extra} />)
  return onSubmit
}

const fill = (name: string, level: string) => {
  fireEvent.change(screen.getByLabelText('Nombre del cliente'), { target: { value: name } })
  fireEvent.change(screen.getByLabelText('Nivel de servicio'), { target: { value: level } })
}

describe('CustomerForm', () => {
  it('cannot be submitted until a name and a level are chosen', () => {
    setup()
    expect(screen.getByRole('button', { name: 'Crear cuenta' })).toBeDisabled()

    fill('Tienda Sol', '')
    expect(screen.getByRole('button', { name: 'Crear cuenta' })).toBeDisabled()

    fill('Tienda Sol', 'EXPRESS')
    expect(screen.getByRole('button', { name: 'Crear cuenta' })).toBeEnabled()
  })

  it('submits the trimmed name and chosen level, then clears the form', async () => {
    const onSubmit = setup()

    fill('  Tienda Sol  ', 'ECONOMY')
    fireEvent.click(screen.getByRole('button', { name: 'Crear cuenta' }))

    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith({ name: 'Tienda Sol', serviceLevel: 'ECONOMY' }))
    await waitFor(() => expect(screen.getByLabelText('Nombre del cliente')).toHaveValue(''))
  })

  it('keeps what the user typed when the server rejects the account', async () => {
    const onSubmit = setup(vi.fn().mockResolvedValue(false))

    fill('Tienda Sol', 'EXPRESS')
    fireEvent.click(screen.getByRole('button', { name: 'Crear cuenta' }))

    await waitFor(() => expect(onSubmit).toHaveBeenCalled())
    expect(screen.getByLabelText('Nombre del cliente')).toHaveValue('Tienda Sol')
  })

  it('shows server errors and confirmations', () => {
    setup(undefined, { error: 'Nombre inválido', confirmation: 'Cuenta CUS-006 creada' })

    expect(screen.getByRole('alert')).toHaveTextContent('Nombre inválido')
    expect(screen.getByRole('status')).toHaveTextContent('Cuenta CUS-006 creada')
  })

  it('disables the button while the request is in flight', () => {
    setup(undefined, { pending: true })

    expect(screen.getByRole('button', { name: 'Creando…' })).toBeDisabled()
  })
})
