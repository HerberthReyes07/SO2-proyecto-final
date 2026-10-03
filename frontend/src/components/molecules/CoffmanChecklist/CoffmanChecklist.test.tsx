import { render, screen } from '@testing-library/react'
import { CoffmanChecklist } from './CoffmanChecklist'

const CONDITIONS = [
  { id: 'a', name: 'Exclusión mutua', explanation: 'Un recurso lo usa una operación a la vez.' },
  { id: 'b', name: 'Espera circular', explanation: 'A espera a B y B espera a A.' },
]

describe('CoffmanChecklist', () => {
  it('lists every condition with its explanation', () => {
    render(<CoffmanChecklist conditions={CONDITIONS} />)

    expect(screen.getByRole('list', { name: 'Condiciones de Coffman' })).toBeInTheDocument()
    expect(screen.getByText('Exclusión mutua')).toBeInTheDocument()
    expect(screen.getByText('A espera a B y B espera a A.')).toBeInTheDocument()
  })

  it('marks each one as fulfilled', () => {
    render(<CoffmanChecklist conditions={CONDITIONS} />)

    expect(screen.getAllByLabelText('Se cumple')).toHaveLength(2)
  })
})
