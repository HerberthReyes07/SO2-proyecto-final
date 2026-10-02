import { MAX_ORDER_LINES, MAX_QUANTITY_PER_LINE } from '@/constants/orderLimits'
import type { PlaceOrderRequest } from '@/types/catalog'

/** Línea del formulario de pedido, antes de enviarse. `key` solo sirve para que React identifique la fila. */
export interface OrderLineDraft {
  key: number
  productId: string
  quantity: number
}

export function createEmptyLine(key: number): OrderLineDraft {
  return { key, productId: '', quantity: 1 }
}

export function totalUnits(lines: readonly OrderLineDraft[]): number {
  return lines.reduce((sum, line) => sum + (Number.isInteger(line.quantity) ? line.quantity : 0), 0)
}

/** Devuelve por qué el borrador no se puede enviar, o null si es válido. */
export function validateDraft(customerId: string, lines: readonly OrderLineDraft[]): string | null {
  if (!customerId) return 'Seleccione un cliente.'
  if (lines.length === 0) return 'Agregue al menos un producto.'
  if (lines.length > MAX_ORDER_LINES) return `Un pedido admite como máximo ${MAX_ORDER_LINES} productos distintos.`
  if (lines.some((line) => !line.productId)) return 'Seleccione un producto en cada línea.'

  const invalidQuantity = lines.some(
    (line) => !Number.isInteger(line.quantity) || line.quantity < 1 || line.quantity > MAX_QUANTITY_PER_LINE,
  )
  if (invalidQuantity) return `La cantidad debe ser un entero entre 1 y ${MAX_QUANTITY_PER_LINE}.`
  return null
}

export function toPlaceOrderRequest(customerId: string, lines: readonly OrderLineDraft[]): PlaceOrderRequest {
  return {
    customerId,
    lines: lines.map(({ productId, quantity }) => ({ productId, quantity })),
  }
}
