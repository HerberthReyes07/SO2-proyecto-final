import type { CatalogResponse, Customer, ServiceLevelInfo } from '@/types/catalog'

/** Opción de un `<select>`: valor enviado y texto mostrado. */
export interface Option {
  value: string
  label: string
}

/** Describe el tiempo objetivo de un nivel: el nivel 1 es "casi instantáneo" (enunciado). */
export function describeTargetWait(level: ServiceLevelInfo): string {
  return level.targetWaitMinutes <= 1 ? 'casi instantáneo' : `espera máx. ${level.targetWaitMinutes} min`
}

export function buildServiceLevelOptions(levels: readonly ServiceLevelInfo[]): Option[] {
  return levels.map((level) => ({
    value: level.id,
    label: `Nivel ${level.level} · ${level.displayName} (${describeTargetWait(level)})`,
  }))
}

/** "Nombre del producto · Tipo de mercancía". Si el tipo no se conoce se muestra su id. */
export function buildProductOptions(catalog: CatalogResponse): Option[] {
  const typeNames = new Map(catalog.merchandiseTypes.map((type) => [type.id, type.displayName]))
  return catalog.products.map((product) => ({
    value: product.id,
    label: `${product.name} · ${typeNames.get(product.type) ?? product.type}`,
  }))
}

/** "Nombre del cliente · Nivel de servicio". */
export function buildCustomerOptions(customers: readonly Customer[], levels: readonly ServiceLevelInfo[]): Option[] {
  const names = serviceLevelNames(levels)
  return customers.map((customer) => ({
    value: customer.id,
    label: `${customer.name} · ${names[customer.level] ?? customer.level}`,
  }))
}

/** Diccionario id de nivel → nombre mostrado. */
export function serviceLevelNames(levels: readonly ServiceLevelInfo[]): Record<string, string> {
  return Object.fromEntries(levels.map((level) => [level.id, level.displayName]))
}
