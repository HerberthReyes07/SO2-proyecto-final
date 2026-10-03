import { render, screen } from '@testing-library/react'
import { LocationCell } from './LocationCell'

describe('LocationCell', () => {
  it('describes the location for assistive technology and as a tooltip', () => {
    render(<LocationCell label="A1-P1-N1 · libre" fillRatio={0} />)

    const cell = screen.getByRole('img', { name: 'A1-P1-N1 · libre' })
    expect(cell).toHaveAttribute('title', 'A1-P1-N1 · libre')
  })

  it('fills from the bottom up to the occupied fraction', () => {
    render(<LocationCell label="ocupada" fillRatio={0.4} color="var(--color-type-heavy)" />)

    expect(screen.getByRole('img', { name: 'ocupada' })).toHaveStyle({ '--fill': '40%' })
  })

  it('keeps the fill inside 0 to 100 percent', () => {
    const { rerender } = render(<LocationCell label="x" fillRatio={3} color="red" />)
    expect(screen.getByRole('img', { name: 'x' })).toHaveStyle({ '--fill': '100%' })

    rerender(<LocationCell label="x" fillRatio={-1} color="red" />)
    expect(screen.getByRole('img', { name: 'x' })).toHaveStyle({ '--fill': '0%' })
  })
})
