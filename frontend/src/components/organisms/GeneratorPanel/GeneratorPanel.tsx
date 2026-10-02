import { useState } from 'react'
import { Badge } from '@/components/atoms/Badge'
import { Button } from '@/components/atoms/Button'
import { Input } from '@/components/atoms/Input'
import { FormField } from '@/components/molecules/FormField'
import { IntensitySelector } from '@/components/molecules/IntensitySelector'
import { INTENSITY_PRESETS } from '@/constants/intensityPresets'
import type { GeneratorState } from '@/types/simulation'
import { parseSeed } from '@/utils/formatStats'
import styles from './GeneratorPanel.module.css'

export interface GeneratorPanelProps {
  generator: GeneratorState
  /** Cambia la intensidad (pedidos por minuto simulado) sin detener ni arrancar el generador. */
  onIntensityChange: (ordersPerMinute: number) => void
  /** Arranca o detiene el generador; al arrancar puede indicar una semilla para repetir una secuencia. */
  onEnabledChange: (enabled: boolean, seed?: number) => void
  pending: boolean
  error?: string | null
}

/** Control del generador automático de pedidos: encender, intensidad y semilla. */
export function GeneratorPanel({ generator, onIntensityChange, onEnabledChange, pending, error }: GeneratorPanelProps) {
  const [seedText, setSeedText] = useState('')
  const seed = parseSeed(seedText)
  const seedIsInvalid = seedText.trim() !== '' && seed === null

  const handleToggle = () => {
    if (generator.enabled) {
      onEnabledChange(false)
    } else if (!seedIsInvalid) {
      onEnabledChange(true, seed ?? undefined)
    }
  }

  return (
    <section className={styles.panel} aria-labelledby="generator-title">
      <header className={styles.header}>
        <h2 id="generator-title" className={styles.title}>
          Generador automático
        </h2>
        <Badge tone={generator.enabled ? 'success' : 'neutral'}>{generator.enabled ? 'Activo' : 'Detenido'}</Badge>
      </header>

      <IntensitySelector presets={INTENSITY_PRESETS} value={generator.ordersPerMinute} onChange={onIntensityChange} />
      <p className={styles.note}>
        {generator.ordersPerMinute} pedidos por minuto simulado · {generator.generated} generados
      </p>

      <FormField
        label="Semilla (opcional)"
        htmlFor="generator-seed"
        error={seedIsInvalid ? 'La semilla debe ser un número entero.' : null}
      >
        <Input
          id="generator-seed"
          inputMode="numeric"
          placeholder="Vacío: se conserva la actual"
          value={seedText}
          invalid={seedIsInvalid}
          disabled={generator.enabled}
          onChange={(event) => setSeedText(event.target.value)}
        />
      </FormField>
      <p className={styles.note}>
        Semilla vigente: <strong>{generator.seed}</strong>. La misma semilla repite la misma secuencia de pedidos.
      </p>

      <Button variant="primary" disabled={pending || (!generator.enabled && seedIsInvalid)} onClick={handleToggle}>
        {generator.enabled ? 'Detener generador' : 'Iniciar generador'}
      </Button>

      {error && (
        <p className={styles.error} role="alert">
          {error}
        </p>
      )}
    </section>
  )
}
