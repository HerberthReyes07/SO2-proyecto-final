import { getJson } from '@/services/apiClient'
import type { CatalogResponse } from '@/types/catalog'

export function fetchCatalog(): Promise<CatalogResponse> {
  return getJson<CatalogResponse>('/api/catalog')
}
