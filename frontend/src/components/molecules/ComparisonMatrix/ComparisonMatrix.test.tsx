import { render, screen, within } from '@testing-library/react'
import type { MatrixRow } from '@/utils/benchmarkReport'
import { ComparisonMatrix } from './ComparisonMatrix'

const COLUMNS = [
  { id: 'FIFO', label: 'Orden de llegada' },
  { id: 'STRICT', label: 'Prioridad estricta', extra: <span>En uso</span> },
]
const ROWS: MatrixRow[] = [
  { id: 'wait', label: 'Espera promedio', cells: [{ text: '2 min', best: false }, { text: '1 min', best: true }] },
  { id: 'util', label: 'Ocupación', cells: [{ text: '80%', best: false }, { text: '90%', best: false }] },
]

describe('ComparisonMatrix', () => {
  it('shows the caption, a column per policy and a row per metric', () => {
    render(<ComparisonMatrix caption="Resumen" columns={COLUMNS} rows={ROWS} />)

    expect(screen.getByRole('table', { name: 'Resumen' })).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: /Prioridad estricta/ })).toBeInTheDocument()
    const row = within(screen.getByRole('row', { name: /^Espera promedio/ }))
    expect(row.getByText('2 min')).toBeInTheDocument()
  })

  it('renders the extra content of a column', () => {
    render(<ComparisonMatrix caption="Resumen" columns={COLUMNS} rows={ROWS} />)

    expect(screen.getByText('En uso')).toBeInTheDocument()
  })

  it('announces the best value to screen readers, not only with color', () => {
    render(<ComparisonMatrix caption="Resumen" columns={COLUMNS} rows={ROWS} />)

    expect(screen.getAllByText('(mejor)')).toHaveLength(1)
    expect(screen.getByRole('cell', { name: /^1 min\s*\(mejor\)$/ })).toBeInTheDocument()
  })
})
