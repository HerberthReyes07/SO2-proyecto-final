import type { AisleCell } from '@/components/molecules/AisleGrid'
import type { WarehouseState } from '@/types/simulation'
import { locationLabel } from '@/utils/warehouseLocations'

/** Datos del catálogo que el mapa necesita para dar nombre y color a lo que hay en cada ubicación. */
export interface MapLookup {
  /** Diccionario id de producto → nombre mostrado. */
  productNames: Record<string, string>
  /** Diccionario id de producto → id de su tipo de mercancía. */
  productTypeIds: Record<string, string>
}

const UNKNOWN_TYPE_COLOR = 'var(--color-neutral)'

/** Variable CSS con el color de un tipo de mercancía (ej. HEAVY → `var(--color-type-heavy)`). */
export function typeColor(typeId: string | undefined): string {
  return typeId ? `var(--color-type-${typeId.toLowerCase()})` : UNKNOWN_TYPE_COLOR
}

/**
 * Arma, para cada pasillo, las celdas que se dibujan. Cada celda lleva su descripción completa
 * (tooltip), qué tan llena está, el color de su tipo de mercancía y si pertenece al lote resaltado.
 */
export function buildAisles(warehouse: WarehouseState, lookup: MapLookup, highlightedLotId: string | null): AisleCell[][] {
  const { layout } = warehouse
  const perAisle = layout.positions * layout.levels

  return Array.from({ length: layout.aisles }, (_, aisle) =>
    Array.from({ length: perAisle }, (_, offset) => {
      const index = aisle * perAisle + offset
      const content = warehouse.locations[index]
      const label = locationLabel(layout, index)

      if (!content) {
        return { index, label: `${label} · libre`, fillRatio: 0, highlighted: false }
      }
      const name = lookup.productNames[content.productId] ?? content.productId
      return {
        index,
        label: `${label} · ${name} × ${content.units} (lote ${content.lotId})`,
        fillRatio: content.usedVolume / layout.locationCapacity,
        color: typeColor(lookup.productTypeIds[content.productId]),
        highlighted: content.lotId === highlightedLotId,
      }
    }),
  )
}
