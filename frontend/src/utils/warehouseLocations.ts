import type { WarehouseLayout } from '@/types/simulation'

/**
 * Coordenadas de una ubicación a partir de su índice. Refleja la numeración del backend
 * (`WarehouseLayout`): primero por pasillo, luego por posición y, lo que más rápido cambia, por nivel.
 */
export function locationCoordinates(layout: WarehouseLayout, index: number) {
  const perAisle = layout.positions * layout.levels
  return {
    aisle: Math.floor(index / perAisle),
    position: Math.floor(index / layout.levels) % layout.positions,
    level: index % layout.levels,
  }
}

/** Código legible, ej. "A2-P3-N1" (pasillo 2, posición 3, nivel 1; todo desde 1). */
export function locationLabel(layout: WarehouseLayout, index: number): string {
  const { aisle, position, level } = locationCoordinates(layout, index)
  return `A${aisle + 1}-P${position + 1}-N${level + 1}`
}
