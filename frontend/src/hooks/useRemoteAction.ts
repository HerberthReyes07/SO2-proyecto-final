import { useCallback, useEffect, useRef, useState } from 'react'
import { errorMessage } from '@/utils/errorMessage'

interface RemoteAction<A extends unknown[], R> {
  /** Ejecuta la acción. Devuelve su resultado, o null si falló (el motivo queda en `error`). */
  run: (...args: A) => Promise<R | null>
  pending: boolean
  error: string | null
  /** Resultado de la última ejecución exitosa. */
  result: R | null
}

/** Envuelve una acción contra el backend (crear cliente, registrar pedido) con su estado de carga y error. */
export function useRemoteAction<A extends unknown[], R>(action: (...args: A) => Promise<R>): RemoteAction<A, R> {
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [result, setResult] = useState<R | null>(null)
  const mounted = useRef(true)

  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
    }
  }, [])

  const run = useCallback(
    async (...args: A) => {
      setPending(true)
      setError(null)
      try {
        const outcome = await action(...args)
        if (mounted.current) setResult(outcome)
        return outcome
      } catch (cause) {
        if (mounted.current) {
          setError(errorMessage(cause, 'La operación falló'))
          setResult(null)
        }
        return null
      } finally {
        if (mounted.current) setPending(false)
      }
    },
    [action],
  )

  return { run, pending, error, result }
}
