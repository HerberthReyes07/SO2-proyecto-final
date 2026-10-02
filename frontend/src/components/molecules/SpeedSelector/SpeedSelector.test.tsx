import { fireEvent, render, screen } from '@testing-library/react'
import { SpeedSelector } from './SpeedSelector'

describe('SpeedSelector', () => {
  it('marks only the active speed as pressed', () => {
    render(<SpeedSelector speeds={[1, 60, 300]} value={60} onChange={() => {}} />)

    expect(screen.getByRole('button', { name: '×60' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: '×1' })).toHaveAttribute('aria-pressed', 'false')
  })

  it('reports the chosen speed', () => {
    const onChange = vi.fn()
    render(<SpeedSelector speeds={[1, 60, 300]} value={60} onChange={onChange} />)

    fireEvent.click(screen.getByRole('button', { name: '×300' }))

    expect(onChange).toHaveBeenCalledWith(300)
  })
})
