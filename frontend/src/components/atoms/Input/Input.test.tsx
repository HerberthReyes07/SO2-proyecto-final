import { fireEvent, render, screen } from '@testing-library/react'
import { Input } from './Input'

describe('Input', () => {
  it('reports typed text', () => {
    const onChange = vi.fn()
    render(<Input aria-label="Nombre" value="" onChange={onChange} />)

    fireEvent.change(screen.getByLabelText('Nombre'), { target: { value: 'Ana' } })

    expect(onChange).toHaveBeenCalledTimes(1)
  })

  it('marks itself as invalid for assistive technology', () => {
    render(<Input aria-label="Nombre" invalid readOnly value="" />)

    expect(screen.getByLabelText('Nombre')).toHaveAttribute('aria-invalid', 'true')
  })
})
