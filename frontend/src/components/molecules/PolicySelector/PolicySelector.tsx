import { DescribedSelect } from '@/components/molecules/DescribedSelect'
import type { PolicyId, PolicyInfo } from '@/types/catalog'

export interface PolicySelectorProps {
  policies: readonly PolicyInfo[]
  value: PolicyId
  onChange: (policy: PolicyId) => void
  /** Error de la última vez que se intentó cambiar la política. */
  error?: string | null
}

/** Elige la política de atención y explica cómo ordena. */
export function PolicySelector({ policies, value, onChange, error }: PolicySelectorProps) {
  return (
    <DescribedSelect
      id="policy-select"
      label="Política de atención"
      options={policies}
      value={value}
      onChange={onChange}
      error={error}
    />
  )
}
