import { render, screen } from '@testing-library/react'
import { StatCard } from './StatCard'

describe('StatCard', () => {
  it('shows the label and the value', () => {
    render(<StatCard label="Completados" value="42" />)

    expect(screen.getByText('Completados')).toBeInTheDocument()
    expect(screen.getByText('42')).toBeInTheDocument()
  })

  it('shows the hint only when given', () => {
    const { rerender } = render(<StatCard label="Espera" value="3 min" />)
    expect(screen.queryByText('simulados')).not.toBeInTheDocument()

    rerender(<StatCard label="Espera" value="3 min" hint="simulados" />)

    expect(screen.getByText('simulados')).toBeInTheDocument()
  })
})
