import { fireEvent, render, screen } from '@testing-library/react'
import { Select } from './Select'

const OPTIONS = [
  { value: 'a', label: 'Opción A' },
  { value: 'b', label: 'Opción B' },
]

describe('Select', () => {
  it('shows the placeholder when nothing is selected', () => {
    render(<Select aria-label="Cliente" options={OPTIONS} placeholder="Seleccione" value="" onChange={() => {}} />)

    expect(screen.getByLabelText('Cliente')).toHaveDisplayValue('Seleccione')
  })

  it('reports the chosen value', () => {
    const onChange = vi.fn()
    render(<Select aria-label="Cliente" options={OPTIONS} value="a" onChange={onChange} />)

    fireEvent.change(screen.getByLabelText('Cliente'), { target: { value: 'b' } })

    expect(onChange).toHaveBeenCalledTimes(1)
  })

  it('renders every option', () => {
    render(<Select aria-label="Cliente" options={OPTIONS} value="a" onChange={() => {}} />)

    expect(screen.getAllByRole('option')).toHaveLength(2)
  })
})
