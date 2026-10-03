import { fireEvent, render, screen } from '@testing-library/react'
import { AlertBanner } from './AlertBanner'

describe('AlertBanner', () => {
  it('announces the message as an alert', () => {
    render(<AlertBanner message="Deadlock detectado" />)

    expect(screen.getByRole('alert')).toHaveTextContent('Deadlock detectado')
  })

  it('offers an action when given one', () => {
    const onAction = vi.fn()
    render(<AlertBanner message="Deadlock detectado" actionLabel="Ver y resolver" onAction={onAction} />)

    fireEvent.click(screen.getByRole('button', { name: 'Ver y resolver' }))

    expect(onAction).toHaveBeenCalledTimes(1)
  })

  it('shows no button without an action', () => {
    render(<AlertBanner message="Deadlock detectado" />)

    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })
})
