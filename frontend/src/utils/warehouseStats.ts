import type { WarehouseState } from '@/types/simulation'

const PERCENT = 100

/** Porcentaje de ubicaciones ocupadas, entre 0 y 100. */
export function occupancyPercent(warehouse: Pick<WarehouseState, 'usedLocations' | 'totalLocations'>): number {
  return warehouse.totalLocations === 0 ? 0 : Math.round((warehouse.usedLocations / warehouse.totalLocations) * PERCENT)
}

/**
 * Porcentaje del volumen de las ubicaciones ocupadas que se pierde por fragmentación interna (la
 * última ubicación de cada lote suele quedar a medias y no se comparte entre lotes).
 */
export function wastePercent(warehouse: Pick<WarehouseState, 'usedLocations' | 'wastedVolume' | 'layout'>): number {
  const usedCapacity = warehouse.usedLocations * warehouse.layout.locationCapacity
  return usedCapacity === 0 ? 0 : Math.round((warehouse.wastedVolume / usedCapacity) * PERCENT)
}
