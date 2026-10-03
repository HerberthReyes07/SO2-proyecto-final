import { sendJson } from '@/services/apiClient'
import type { ArrivalConfirmation, RegisterArrivalRequest } from '@/types/catalog'

/** Registra la llegada de mercadería; lo que sigue (buffer, inventario, reevaluar pedidos) es automático. */
export function registerArrival(request: RegisterArrivalRequest): Promise<ArrivalConfirmation> {
  return sendJson<ArrivalConfirmation>('/api/receptions', 'POST', request)
}
