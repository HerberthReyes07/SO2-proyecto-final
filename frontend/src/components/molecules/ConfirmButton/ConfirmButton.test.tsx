import { fireEvent, render, screen } from '@testing-library/react'
import { ConfirmButton } from './ConfirmButton'

function setup(extra: Partial<React.ComponentProps<typeof ConfirmButton>> = {}) {
  const onConfirm = vi.fn()
  render(
    <ConfirmButton
      label="Reiniciar simulación"
      question="¿Seguro? Se pierde el estado."
      confirmLabel="Sí, reiniciar"
      onConfirm={onConfirm}
      {...extra}
    />,
  )
  return onConfirm
}

describe('ConfirmButton', () => {
  it('does not run the action on the first click: it asks first', () => {
    const onConfirm = setup()

    fireEvent.click(screen.getByRole('button', { name: 'Reiniciar simulación' }))

    expect(onConfirm).not.toHaveBeenCalled()
    expect(screen.getByRole('group', { name: '¿Seguro? Se pierde el estado.' })).toBeInTheDocument()
  })

  it('runs the action once confirmed and goes back to the initial button', () => {
    const onConfirm = setup()

    fireEvent.click(screen.getByRole('button', { name: 'Reiniciar simulación' }))
    fireEvent.click(screen.getByRole('button', { name: 'Sí, reiniciar' }))

    expect(onConfirm).toHaveBeenCalledTimes(1)
    expect(screen.getByRole('button', { name: 'Reiniciar simulación' })).toBeInTheDocument()
  })

  it('does nothing when cancelled', () => {
    const onConfirm = setup()

    fireEvent.click(screen.getByRole('button', { name: 'Reiniciar simulación' }))
    fireEvent.click(screen.getByRole('button', { name: 'Cancelar' }))

    expect(onConfirm).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: 'Reiniciar simulación' })).toBeInTheDocument()
  })

  it('cannot be started while disabled', () => {
    setup({ disabled: true })

    expect(screen.getByRole('button', { name: 'Reiniciar simulación' })).toBeDisabled()
  })
})
