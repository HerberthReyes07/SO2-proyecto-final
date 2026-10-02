/** Extrae un mensaje legible de un error capturado (que en JavaScript puede ser cualquier cosa). */
export function errorMessage(cause: unknown, fallback: string): string {
  return cause instanceof Error && cause.message ? cause.message : fallback
}
