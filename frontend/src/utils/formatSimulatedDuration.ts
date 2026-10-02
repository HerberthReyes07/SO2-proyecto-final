const MS_PER_SECOND = 1000
const SECONDS_PER_MINUTE = 60
const MINUTES_PER_HOUR = 60

/** Da formato a una duración simulada: "45 s", "2 min 10 s" o "1 h 05 min". */
export function formatSimulatedDuration(millis: number): string {
  const totalSeconds = Math.max(0, Math.round(millis / MS_PER_SECOND))
  const totalMinutes = Math.floor(totalSeconds / SECONDS_PER_MINUTE)
  const seconds = totalSeconds % SECONDS_PER_MINUTE

  if (totalMinutes === 0) return `${seconds} s`
  if (totalMinutes < MINUTES_PER_HOUR) return `${totalMinutes} min ${seconds} s`

  const hours = Math.floor(totalMinutes / MINUTES_PER_HOUR)
  const minutes = totalMinutes % MINUTES_PER_HOUR
  return `${hours} h ${String(minutes).padStart(2, '0')} min`
}
