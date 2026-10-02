import { Button } from '@/components/atoms/Button'
import styles from './SpeedSelector.module.css'

export interface SpeedSelectorProps {
  speeds: readonly number[]
  value: number
  onChange: (speed: number) => void
}

/** Grupo de botones para elegir el factor de aceleración; marca el que está activo. */
export function SpeedSelector({ speeds, value, onChange }: SpeedSelectorProps) {
  return (
    <div className={styles.group} role="group" aria-label="Velocidad de simulación">
      {speeds.map((speed) => (
        <Button key={speed} pressed={speed === value} onClick={() => onChange(speed)}>
          ×{speed}
        </Button>
      ))}
    </div>
  )
}
