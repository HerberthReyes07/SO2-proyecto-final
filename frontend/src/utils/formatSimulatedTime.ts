const MS_PER_SECOND = 1000
const SECONDS_PER_MINUTE = 60
const SECONDS_PER_HOUR = 3600
const SECONDS_PER_DAY = 86_400

const twoDigits = (value: number) => String(value).padStart(2, '0')

/** Da formato legible al tiempo simulado transcurrido: "Día 1 · 00:12:30". La simulación inicia en el día 1. */
export function formatSimulatedTime(simulatedMillis: number): string {
  const totalSeconds = Math.max(0, Math.floor(simulatedMillis / MS_PER_SECOND))
  const day = Math.floor(totalSeconds / SECONDS_PER_DAY) + 1
  const secondsOfDay = totalSeconds % SECONDS_PER_DAY
  const hours = Math.floor(secondsOfDay / SECONDS_PER_HOUR)
  const minutes = Math.floor((secondsOfDay % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE)
  const seconds = secondsOfDay % SECONDS_PER_MINUTE

  return `Día ${day} · ${twoDigits(hours)}:${twoDigits(minutes)}:${twoDigits(seconds)}`
}
