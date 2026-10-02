import { fireEvent, render, screen } from '@testing-library/react'
import { IntensitySelector } from './IntensitySelector'

const PRESETS = [
  { label: 'Baja', ordersPerMinute: 0.5 },
  { label: 'Normal', ordersPerMinute: 1 },
]

describe('IntensitySelector', () => {
  it('marks only the current intensity as pressed', () => {
    render(<IntensitySelector presets={PRESETS} value={1} onChange={() => {}} />)

    expect(screen.getByRole('button', { name: 'Normal' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: 'Baja' })).toHaveAttribute('aria-pressed', 'false')
  })

  it('marks none when the current intensity is not a preset', () => {
    render(<IntensitySelector presets={PRESETS} value={2.2} onChange={() => {}} />)

    expect(screen.queryAllByRole('button', { pressed: true })).toHaveLength(0)
  })

  it('reports the chosen intensity', () => {
    const onChange = vi.fn()
    render(<IntensitySelector presets={PRESETS} value={1} onChange={onChange} />)

    fireEvent.click(screen.getByRole('button', { name: 'Baja' }))

    expect(onChange).toHaveBeenCalledWith(0.5)
  })
})
