import type { TruckPhase } from '@/types/simulation'

/** Nombre mostrado al usuario para cada fase de un camión en Recepción. */
export const TRUCK_PHASE_LABELS: Record<TruckPhase, string> = {
  WAITING_RESOURCES: 'Esperando escáner o andén',
  UNLOADING: 'Descargando',
  REPORTING: 'Informando a Inventario',
}
