import { fireEvent, render, screen } from '@testing-library/react'
import { Button } from './Button'

describe('Button', () => {
  it('calls onClick when clicked', () => {
    const onClick = vi.fn()
    render(<Button onClick={onClick}>Pausar</Button>)

    fireEvent.click(screen.getByRole('button', { name: 'Pausar' }))

    expect(onClick).toHaveBeenCalledTimes(1)
  })

  it('does not call onClick when disabled', () => {
    const onClick = vi.fn()
    render(<Button onClick={onClick} disabled>Pausar</Button>)

    fireEvent.click(screen.getByRole('button', { name: 'Pausar' }))

    expect(onClick).not.toHaveBeenCalled()
  })

  it('exposes its pressed state to assistive technology', () => {
    render(<Button pressed>×60</Button>)

    expect(screen.getByRole('button', { name: '×60' })).toHaveAttribute('aria-pressed', 'true')
  })

  it('is a plain button by default so it never submits a form by accident', () => {
    render(<Button>Ok</Button>)

    expect(screen.getByRole('button', { name: 'Ok' })).toHaveAttribute('type', 'button')
  })
})
