import { render, screen } from '@testing-library/react'
import { ProgressBar } from './ProgressBar'

describe('ProgressBar', () => {
  it('exposes its progress to assistive technology', () => {
    render(<ProgressBar value={3} max={6} label="Ocupación" />)

    const bar = screen.getByRole('progressbar', { name: 'Ocupación' })
    expect(bar).toHaveAttribute('aria-valuenow', '3')
    expect(bar).toHaveAttribute('aria-valuemax', '6')
  })

  it('does not break when max is zero', () => {
    render(<ProgressBar value={0} max={0} label="Vacía" />)

    expect(screen.getByRole('progressbar', { name: 'Vacía' })).toBeInTheDocument()
  })
})
