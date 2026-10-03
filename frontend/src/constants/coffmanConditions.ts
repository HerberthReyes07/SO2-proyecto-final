/**
 * Las cuatro condiciones de Coffman: un deadlock existe solo si se cumplen las cuatro a la vez. Aquí se
 * explica cómo se manifiesta cada una en REDXela. La cuarta (espera circular) depende del conflicto
 * concreto y se redacta aparte (ver `explainConflict`).
 */
export interface CoffmanCondition {
  id: 'mutual-exclusion' | 'hold-and-wait' | 'no-preemption' | 'circular-wait'
  name: string
  explanation: string
}

export const STATIC_COFFMAN_CONDITIONS: readonly CoffmanCondition[] = [
  {
    id: 'mutual-exclusion',
    name: 'Exclusión mutua',
    explanation: 'Cada unidad de escáner o de área de carga la usa una sola operación a la vez.',
  },
  {
    id: 'hold-and-wait',
    name: 'Retención y espera',
    explanation: 'Cada operación bloqueada retiene un recurso mientras espera otro.',
  },
  {
    id: 'no-preemption',
    name: 'Sin apropiación',
    explanation: 'Nadie puede quitarle un recurso a una operación: solo lo libera ella al terminar.',
  },
]
