import { useMemo } from 'react'
import type { Option } from '@/utils/options'
import type { CatalogResponse, Customer } from '@/types/catalog'
import {
  buildCustomerOptions,
  buildProductOptions,
  buildServiceLevelOptions,
  productNames,
  serviceLevelNames,
} from '@/utils/options'

interface ReferenceOptions {
  serviceLevelOptions: Option[]
  /** Diccionario id de nivel de servicio → nombre mostrado. */
  serviceLevelNames: Record<string, string>
  productOptions: Option[]
  /** Diccionario id de producto → nombre mostrado. */
  productNames: Record<string, string>
  customerOptions: Option[]
}

/**
 * Convierte el catálogo y los clientes (que llegan del backend) en las opciones de los formularios.
 * Se memoriza para no reconstruir las listas en cada snapshot, que llega varias veces por segundo.
 */
export function useReferenceOptions(catalog: CatalogResponse | null, customers: Customer[] | null): ReferenceOptions {
  const levels = catalog?.serviceLevels
  return useMemo(
    () => ({
      serviceLevelOptions: buildServiceLevelOptions(levels ?? []),
      serviceLevelNames: serviceLevelNames(levels ?? []),
      productOptions: catalog ? buildProductOptions(catalog) : [],
      productNames: catalog ? productNames(catalog) : {},
      customerOptions: buildCustomerOptions(customers ?? [], levels ?? []),
    }),
    [catalog, customers, levels],
  )
}
