import { MAX_QUANTITY_PER_TRUCK, MAX_TRUCKS_PER_REQUEST } from '@/constants/receptionLimits'

/** Devuelve por qué la llegada no se puede enviar, o null si es válida. */
export function validateArrival(productId: string, quantity: number, trucks: number): string | null {
  if (!productId) return 'Seleccione el producto que llegó.'
  if (!Number.isInteger(quantity) || quantity < 1 || quantity > MAX_QUANTITY_PER_TRUCK) {
    return `Las unidades por camión deben ser un entero entre 1 y ${MAX_QUANTITY_PER_TRUCK}.`
  }
  if (!Number.isInteger(trucks) || trucks < 1 || trucks > MAX_TRUCKS_PER_REQUEST) {
    return `Los camiones deben ser un entero entre 1 y ${MAX_TRUCKS_PER_REQUEST}.`
  }
  return null
}
