import { sendJson } from '@/services/apiClient'
import type { PolicyId } from '@/types/catalog'

/** Cambia la política de atención; el servidor devuelve la que quedó vigente. */
export function changePolicy(policy: PolicyId): Promise<{ policy: PolicyId }> {
  return sendJson<{ policy: PolicyId }>('/api/scheduling/policy', 'PUT', { policy })
}
