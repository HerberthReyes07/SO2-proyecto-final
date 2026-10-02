import type { Stage } from '@/types/simulation'

/** Nombre mostrado al usuario para cada etapa del flujo de un pedido. */
export const STAGE_LABELS: Record<Stage, string> = {
  PICKING: 'Picking',
  INSPECTION: 'Control de calidad',
  PACKING: 'Empaque',
  DISPATCH: 'Despacho',
}
