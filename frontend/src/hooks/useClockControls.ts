import { useCallback, useState } from 'react'
import { updateClock, type ClockUpdate } from '@/services/clockApi'
import { errorMessage } from '@/utils/errorMessage'

interface ClockControls {
  /** Último error al comunicarse con el backend, o null si la última acción tuvo éxito. */
  error: string | null
  setSpeed: (speed: number) => Promise<void>
  setPaused: (paused: boolean) => Promise<void>
}

/**
 * Acciones sobre el reloj simulado. No guarda el estado del reloj: la fuente de verdad es el
 * snapshot que llega por WebSocket, así la interfaz nunca muestra algo que el servidor no aplicó.
 */
export function useClockControls(): ClockControls {
  const [error, setError] = useState<string | null>(null)

  const apply = useCallback(async (update: ClockUpdate) => {
    try {
      await updateClock(update)
      setError(null)
    } catch (cause) {
      setError(errorMessage(cause, 'No se pudo actualizar el reloj'))
    }
  }, [])

  const setSpeed = useCallback((speed: number) => apply({ speed }), [apply])
  const setPaused = useCallback((paused: boolean) => apply({ paused }), [apply])

  return { error, setSpeed, setPaused }
}
