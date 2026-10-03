import type { PreventionModeId } from '@/types/catalog'
import type { ScenarioState } from '@/types/simulation'

export interface ScenarioMessage {
  /** Tono del mensaje: informa, salió bien o salió con deadlock. */
  tone: 'info' | 'success' | 'warning'
  text: string
}

const MODE_LABELS: Record<PreventionModeId, string> = {
  NONE: 'sin prevención',
  GLOBAL_ORDER: 'con orden global',
}

/**
 * Cuenta en una frase cómo va (o cómo terminó) el escenario de demostración. A velocidad normal todo ocurre
 * en un par de segundos, así que sin este mensaje no habría forma de saber que corrió ni cómo salió.
 */
export function describeScenario(scenario: ScenarioState): ScenarioMessage | null {
  if (scenario.phase === 'IDLE' || scenario.mode === null) return null
  const mode = MODE_LABELS[scenario.mode]

  switch (scenario.phase) {
    case 'RUNNING':
      return { tone: 'info', text: `Escenario en curso (${mode})… Mire los recursos en juego aquí abajo.` }
    case 'FINISHED_CLEAN':
      return {
        tone: 'success',
        text: `Escenario terminado (${mode}): no hubo deadlock; el pedido y los camiones se completaron solos.`,
      }
    case 'FINISHED_WITH_DEADLOCK':
      return { tone: 'warning', text: `Escenario terminado (${mode}): se produjo un deadlock y se resolvió.` }
  }
}
