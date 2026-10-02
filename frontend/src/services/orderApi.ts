import { sendJson } from '@/services/apiClient'
import type { OrderConfirmation, PlaceOrderRequest } from '@/types/catalog'

export function placeOrder(request: PlaceOrderRequest): Promise<OrderConfirmation> {
  return sendJson<OrderConfirmation>('/api/orders', 'POST', request)
}
