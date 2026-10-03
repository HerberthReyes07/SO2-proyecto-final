import type { BadgeTone } from '@/components/atoms/Badge'
import { LOW_STOCK_UNITS } from '@/constants/stockThresholds'
import type { MissingStock } from '@/types/simulation'

export interface StockStatus {
  label: string
  tone: BadgeTone
}

/** Etiqueta y tono de un producto según sus unidades disponibles; null si hay de sobra y no hay nada que avisar. */
export function getStockStatus(available: number): StockStatus | null {
  if (available <= 0) return { label: 'Agotado', tone: 'danger' }
  if (available < LOW_STOCK_UNITS) return { label: 'Bajo', tone: 'warning' }
  return null
}

/** "Camiseta (faltan 5), Refrigeradora (faltan 2)": qué le impide a un pedido empezar. */
export function describeMissingStock(missing: readonly MissingStock[], productNames: Record<string, string>): string {
  return missing
    .map((item) => `${productNames[item.productId] ?? item.productId} (faltan ${item.missingUnits})`)
    .join(', ')
}
