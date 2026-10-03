import { fireEvent, render, screen } from '@testing-library/react'
import { DescribedSelect } from './DescribedSelect'

const OPTIONS = [
  { id: 'A', displayName: 'Opción A', description: 'Explicación de A.' },
  { id: 'B', displayName: 'Opción B', description: 'Explicación de B.' },
] as const

describe('DescribedSelect', () => {
  it('shows the current option and explains it', () => {
    render(<DescribedSelect id="sel" label="Criterio" options={OPTIONS} value="B" onChange={() => {}} />)

    expect(screen.getByLabelText('Criterio')).toHaveDisplayValue('Opción B')
    expect(screen.getByText('Explicación de B.')).toBeInTheDocument()
    expect(screen.queryByText('Explicación de A.')).not.toBeInTheDocument()
  })

  it('reports the chosen option id', () => {
    const onChange = vi.fn()
    render(<DescribedSelect id="sel" label="Criterio" options={OPTIONS} value="A" onChange={onChange} />)

    fireEvent.change(screen.getByLabelText('Criterio'), { target: { value: 'B' } })

    expect(onChange).toHaveBeenCalledWith('B')
  })

  it('shows an error when given one', () => {
    render(<DescribedSelect id="sel" label="Criterio" options={OPTIONS} value="A" onChange={() => {}} error="Falló" />)

    expect(screen.getByRole('alert')).toHaveTextContent('Falló')
  })
})
