import { render, screen } from '@testing-library/react'
import { DashboardTemplate } from './DashboardTemplate'

describe('DashboardTemplate', () => {
  it('places the title, status, content and sidebar', () => {
    render(
      <DashboardTemplate title="REDXela" status={<span>Conectado</span>} sidebar={<p>Formularios</p>}>
        <p>Contenido</p>
      </DashboardTemplate>,
    )

    expect(screen.getByRole('heading', { name: 'REDXela' })).toBeInTheDocument()
    expect(screen.getByText('Conectado')).toBeInTheDocument()
    expect(screen.getByRole('main')).toHaveTextContent('Contenido')
    expect(screen.getByRole('complementary')).toHaveTextContent('Formularios')
  })

  it('renders no sidebar when none is given', () => {
    render(
      <DashboardTemplate title="REDXela">
        <p>Contenido</p>
      </DashboardTemplate>,
    )

    expect(screen.queryByRole('complementary')).not.toBeInTheDocument()
  })
})
