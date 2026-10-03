import { render, screen } from '@testing-library/react'
import { TabbedContent } from './TabbedContent'

const TABS = [
  { id: 'overview', label: 'Resumen' },
  { id: 'orders', label: 'Pedidos' },
]

describe('TabbedContent', () => {
  it('shows the tabs and the content of the active one in a labelled panel', () => {
    render(
      <TabbedContent tabs={TABS} activeId="orders" onChange={() => {}} idPrefix="main">
        <p>Contenido de pedidos</p>
      </TabbedContent>,
    )

    expect(screen.getAllByRole('tab')).toHaveLength(2)
    const panel = screen.getByRole('tabpanel', { name: 'Pedidos' })
    expect(panel).toHaveTextContent('Contenido de pedidos')
  })
})
