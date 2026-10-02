import { sendJson } from '@/services/apiClient'

/** Reinicia las estadísticas y la medición de ocupación para medir un experimento desde cero. */
export function resetStats(): Promise<void> {
  return sendJson<void>('/api/stats/reset', 'POST', {})
}
