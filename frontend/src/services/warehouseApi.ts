import { sendJson } from '@/services/apiClient'
import type { PlacementStrategyId } from '@/types/catalog'

/** Cambia la estrategia con que se eligen ubicaciones para los lotes que lleguen; los ya guardados no se mueven. */
export function changeStrategy(strategy: PlacementStrategyId): Promise<{ strategy: PlacementStrategyId }> {
  return sendJson<{ strategy: PlacementStrategyId }>('/api/warehouse/strategy', 'PUT', { strategy })
}
