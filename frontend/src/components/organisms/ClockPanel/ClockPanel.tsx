import { Badge } from '@/components/atoms/Badge'
import { Button } from '@/components/atoms/Button'
import { SpeedSelector } from '@/components/molecules/SpeedSelector'
import { SPEED_PRESETS } from '@/constants/speedPresets'
import type { ClockSnapshot } from '@/types/simulation'
import { formatSimulatedTime } from '@/utils/formatSimulatedTime'
import styles from './ClockPanel.module.css'

export interface ClockPanelProps {
  clock: ClockSnapshot
  onSpeedChange: (speed: number) => void
  onPausedChange: (paused: boolean) => void
  /** Mensaje de error de la última acción, si falló. */
  error?: string | null
}

/** Reloj de la simulación: hora simulada, velocidad y pausa. Solo presenta; las acciones llegan por props. */
export function ClockPanel({ clock, onSpeedChange, onPausedChange, error }: ClockPanelProps) {
  return (
    <section className={styles.panel} aria-label="Reloj de la simulación">
      <time className={styles.time}>{formatSimulatedTime(clock.simulatedMillis)}</time>
      {clock.paused && <Badge tone="warning">En pausa</Badge>}
      <SpeedSelector speeds={SPEED_PRESETS} value={clock.speed} onChange={onSpeedChange} />
      <Button variant="primary" onClick={() => onPausedChange(!clock.paused)}>
        {clock.paused ? 'Reanudar' : 'Pausar'}
      </Button>
      {error && (
        <p className={styles.error} role="alert">
          {error}
        </p>
      )}
    </section>
  )
}
