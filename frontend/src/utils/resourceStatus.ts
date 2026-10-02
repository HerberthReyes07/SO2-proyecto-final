import type { BadgeTone } from '@/components/atoms/Badge'

export interface ResourceStatus {
  label: string
  tone: BadgeTone
}

/** Traduce la ocupación de un pool a una etiqueta y un tono visual. Función pura, fácil de probar. */
export function getResourceStatus(inUse: number, capacity: number): ResourceStatus {
  if (inUse <= 0) return { label: 'Disponible', tone: 'success' }
  if (inUse >= capacity) return { label: 'Saturado', tone: 'danger' }
  return { label: 'En uso', tone: 'warning' }
}
