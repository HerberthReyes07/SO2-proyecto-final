import { Button } from '@/components/atoms/Button'
import type { IntensityPreset } from '@/constants/intensityPresets'
import styles from './IntensitySelector.module.css'

export interface IntensitySelectorProps {
  presets: readonly IntensityPreset[]
  /** Intensidad vigente, en pedidos por minuto simulado. */
  value: number
  onChange: (ordersPerMinute: number) => void
}

/** Grupo de botones para elegir la intensidad del generador; marca la vigente. */
export function IntensitySelector({ presets, value, onChange }: IntensitySelectorProps) {
  return (
    <div className={styles.group} role="group" aria-label="Intensidad de generación">
      {presets.map((preset) => (
        <Button key={preset.label} pressed={preset.ordersPerMinute === value} onClick={() => onChange(preset.ordersPerMinute)}>
          {preset.label}
        </Button>
      ))}
    </div>
  )
}
