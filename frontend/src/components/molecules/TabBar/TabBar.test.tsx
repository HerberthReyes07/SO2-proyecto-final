import { fireEvent, render, screen } from '@testing-library/react'
import { TabBar } from './TabBar'

const TABS = [
  { id: 'overview', label: 'Resumen' },
  { id: 'orders', label: 'Pedidos' },
]

describe('TabBar', () => {
  it('marks only the active tab as selected', () => {
    render(<TabBar tabs={TABS} activeId="orders" onChange={() => {}} idPrefix="main" />)

    expect(screen.getByRole('tab', { name: 'Pedidos' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('tab', { name: 'Resumen' })).toHaveAttribute('aria-selected', 'false')
  })

  it('reports the chosen tab', () => {
    const onChange = vi.fn()
    render(<TabBar tabs={TABS} activeId="overview" onChange={onChange} idPrefix="main" />)

    fireEvent.click(screen.getByRole('tab', { name: 'Pedidos' }))

    expect(onChange).toHaveBeenCalledWith('orders')
  })

  it('links every tab with its panel for assistive technology', () => {
    render(<TabBar tabs={TABS} activeId="overview" onChange={() => {}} idPrefix="main" />)

    const tab = screen.getByRole('tab', { name: 'Resumen' })
    expect(tab).toHaveAttribute('id', 'main-tab-overview')
    expect(tab).toHaveAttribute('aria-controls', 'main-panel')
  })
})
