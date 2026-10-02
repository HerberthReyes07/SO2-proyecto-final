import { render, screen } from '@testing-library/react'
import { Input } from '@/components/atoms/Input'
import { FormField } from './FormField'

describe('FormField', () => {
  it('associates the label with its control', () => {
    render(
      <FormField label="Nombre" htmlFor="name">
        <Input id="name" readOnly value="" />
      </FormField>,
    )

    expect(screen.getByLabelText('Nombre')).toBeInTheDocument()
  })

  it('announces the error when there is one', () => {
    render(
      <FormField label="Nombre" htmlFor="name" error="Obligatorio">
        <Input id="name" readOnly value="" />
      </FormField>,
    )

    expect(screen.getByRole('alert')).toHaveTextContent('Obligatorio')
  })

  it('renders no alert without an error', () => {
    render(
      <FormField label="Nombre" htmlFor="name">
        <Input id="name" readOnly value="" />
      </FormField>,
    )

    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })
})
