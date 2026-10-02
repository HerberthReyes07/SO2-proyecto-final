import type { ResourceType } from '@/types/simulation'

/** Nombre mostrado al usuario para cada clase de recurso. */
export const RESOURCE_LABELS: Record<ResourceType, string> = {
  WAREHOUSE_WORKER: 'Encargados de bodega',
  FORKLIFT: 'Montacargas',
  QUALITY_STATION: 'Control de calidad',
  PACKING_STATION: 'Estaciones de empaque',
  SCANNER: 'Sistema de escaneo',
  LOADING_DOCK: 'Áreas de carga',
}
