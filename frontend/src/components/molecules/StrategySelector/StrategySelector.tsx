import { DescribedSelect } from '@/components/molecules/DescribedSelect'
import type { PlacementStrategyId, StrategyInfo } from '@/types/catalog'

export interface StrategySelectorProps {
  strategies: readonly StrategyInfo[]
  value: PlacementStrategyId
  onChange: (strategy: PlacementStrategyId) => void
  /** Error de la última vez que se intentó cambiar la estrategia. */
  error?: string | null
}

/** Elige cómo se escogen las ubicaciones de los lotes nuevos y explica el criterio. */
export function StrategySelector({ strategies, value, onChange, error }: StrategySelectorProps) {
  return (
    <DescribedSelect
      id="strategy-select"
      label="Estrategia de ubicación"
      options={strategies}
      value={value}
      onChange={onChange}
      error={error}
    />
  )
}
