import { sendJson } from '@/services/apiClient'

/**
 * Pide reiniciar toda la simulación. El servidor responde de inmediato y se reinicia después: la conexión en
 * vivo se cae unos segundos y se reconecta sola.
 */
export function resetSimulation(): Promise<{ status: string }> {
  return sendJson<{ status: string }>('/api/simulation/reset', 'POST', {})
}
