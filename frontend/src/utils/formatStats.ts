const PERCENT = 100

/** "73%" para una fracción entre 0 y 1. */
export function formatPercent(ratio: number): string {
  return `${Math.round(ratio * PERCENT)}%`
}

/** "1,5" (coma decimal; un decimal basta para ver la tendencia). La unidad la pone quien lo muestre. */
export function formatThroughput(ordersPerMinute: number): string {
  return ordersPerMinute.toFixed(1).replace('.', ',')
}

/** Convierte una semilla escrita por el usuario; devuelve null si está vacía o no es un entero seguro. */
export function parseSeed(text: string): number | null {
  const trimmed = text.trim()
  if (!/^-?\d+$/.test(trimmed)) return null
  const value = Number(trimmed)
  return Number.isSafeInteger(value) ? value : null
}

/** Convierte la cantidad de pedidos escrita por el usuario; null si no es un entero dentro de [min, max]. */
export function parseBoundedInteger(text: string, min: number, max: number): number | null {
  const trimmed = text.trim()
  if (!/^\d+$/.test(trimmed)) return null
  const value = Number(trimmed)
  return value >= min && value <= max ? value : null
}
