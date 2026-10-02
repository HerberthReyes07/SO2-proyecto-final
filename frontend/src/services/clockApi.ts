import { sendJson } from '@/services/apiClient'
import type { ClockSnapshot } from '@/types/simulation'

/** Cambios parciales al reloj: los campos ausentes no se modifican en el servidor. */
export interface ClockUpdate {
  speed?: number
  paused?: boolean
}

export function updateClock(update: ClockUpdate): Promise<ClockSnapshot> {
  return sendJson<ClockSnapshot>('/api/clock', 'PATCH', update)
}
