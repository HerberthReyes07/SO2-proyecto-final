import { Select } from '@/components/atoms/Select'
import { FormField } from '@/components/molecules/FormField'
import type { PolicyId, PolicyInfo } from '@/types/catalog'
import styles from './PolicySelector.module.css'

export interface PolicySelectorProps {
  policies: readonly PolicyInfo[]
  value: PolicyId
  onChange: (policy: PolicyId) => void
  /** Error de la última vez que se intentó cambiar la política. */
  error?: string | null
}

/** Elige la política de atención y explica cómo ordena. */
export function PolicySelector({ policies, value, onChange, error }: PolicySelectorProps) {
  const options = policies.map((policy) => ({ value: policy.id, label: policy.displayName }))
  const current = policies.find((policy) => policy.id === value)

  return (
    <div className={styles.selector}>
      <FormField label="Política de atención" htmlFor="policy-select" error={error}>
        <Select
          id="policy-select"
          options={options}
          value={value}
          onChange={(event) => onChange(event.target.value as PolicyId)}
        />
      </FormField>
      {current && <p className={styles.description}>{current.description}</p>}
    </div>
  )
}
