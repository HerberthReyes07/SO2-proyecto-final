import { sendJson } from '@/services/apiClient'
import type { GeneratorState } from '@/types/simulation'

/** Cambios parciales al generador: los campos ausentes no se modifican en el servidor. */
export interface GeneratorUpdate {
  enabled?: boolean
  ordersPerMinute?: number
  seed?: number
}

export function updateGenerator(update: GeneratorUpdate): Promise<GeneratorState> {
  return sendJson<GeneratorState>('/api/generator', 'PUT', update)
}
