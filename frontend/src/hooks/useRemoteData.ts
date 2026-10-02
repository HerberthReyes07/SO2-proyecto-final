import { useCallback, useEffect, useState } from 'react'
import { errorMessage } from '@/utils/errorMessage'

interface RemoteData<T> {
  data: T | null
  error: string | null
  /** Vuelve a pedir los datos (ej. tras crear un cliente). */
  reload: () => void
}

/**
 * Pide datos al backend al montarse. `fetcher` debe ser una función estable (definida fuera del
 * componente), porque un cambio de identidad dispara una nueva petición.
 */
export function useRemoteData<T>(fetcher: () => Promise<T>): RemoteData<T> {
  const [data, setData] = useState<T | null>(null)
  const [error, setError] = useState<string | null>(null)
  // Cambiar `version` vuelve a ejecutar el efecto: así `reload` no necesita llamar a setState dentro de él.
  const [version, setVersion] = useState(0)

  useEffect(() => {
    // Si el componente se desmonta (o se pide otra carga) antes de que responda el servidor,
    // la respuesta vieja se descarta en lugar de pisar el estado.
    let cancelled = false
    fetcher().then(
      (result) => {
        if (cancelled) return
        setData(result)
        setError(null)
      },
      (cause: unknown) => {
        if (!cancelled) setError(errorMessage(cause, 'No se pudieron cargar los datos'))
      },
    )
    return () => {
      cancelled = true
    }
  }, [fetcher, version])

  const reload = useCallback(() => setVersion((current) => current + 1), [])

  return { data, error, reload }
}
