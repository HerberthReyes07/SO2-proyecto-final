import { Select } from '@/components/atoms/Select'
import { FormField } from '@/components/molecules/FormField'
import styles from './DescribedSelect.module.css'

/** Una opción con su explicación. */
export interface DescribedOption<Id extends string> {
  id: Id
  displayName: string
  description: string
}

export interface DescribedSelectProps<Id extends string> {
  /** Id del `<select>`; asocia la etiqueta con él. */
  id: string
  label: string
  options: readonly DescribedOption<Id>[]
  value: Id
  onChange: (value: Id) => void
  error?: string | null
}

/** Selector que, debajo, explica en qué consiste la opción elegida. */
export function DescribedSelect<Id extends string>({ id, label, options, value, onChange, error }: DescribedSelectProps<Id>) {
  const selectOptions = options.map((option) => ({ value: option.id, label: option.displayName }))
  const current = options.find((option) => option.id === value)

  return (
    <div className={styles.selector}>
      <FormField label={label} htmlFor={id} error={error}>
        <Select id={id} options={selectOptions} value={value} onChange={(event) => onChange(event.target.value as Id)} />
      </FormField>
      {current && <p className={styles.description}>{current.description}</p>}
    </div>
  )
}
