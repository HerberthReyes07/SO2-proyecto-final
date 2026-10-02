import { useEffect, useState } from 'react'
import { buildSocketUrl } from '@/services/socketUrl'
import type { ConnectionStatus, SimulationSnapshot } from '@/types/simulation'

const RECONNECT_DELAY_MS = 2000

interface SimulationStream {
  snapshot: SimulationSnapshot | null
  status: ConnectionStatus
}

/**
 * Mantiene una conexión WebSocket con el backend y expone la última foto recibida.
 * Si la conexión se cae, reintenta cada {@link RECONNECT_DELAY_MS} ms hasta que el componente se desmonte.
 */
export function useSimulationStream(): SimulationStream {
  const [snapshot, setSnapshot] = useState<SimulationSnapshot | null>(null)
  const [status, setStatus] = useState<ConnectionStatus>('connecting')

  useEffect(() => {
    let socket: WebSocket | null = null
    let retryTimer: ReturnType<typeof setTimeout> | undefined
    let disposed = false

    const connect = () => {
      setStatus('connecting')
      socket = new WebSocket(buildSocketUrl())

      socket.onopen = () => setStatus('open')
      socket.onmessage = (event: MessageEvent<string>) => {
        try {
          setSnapshot(JSON.parse(event.data) as SimulationSnapshot)
        } catch {
          // Un mensaje malformado se descarta; el siguiente snapshot lo reemplaza de todos modos.
        }
      }
      socket.onclose = () => {
        if (disposed) return
        setStatus('closed')
        retryTimer = setTimeout(connect, RECONNECT_DELAY_MS)
      }
    }

    connect()

    return () => {
      disposed = true
      clearTimeout(retryTimer)
      socket?.close()
    }
  }, [])

  return { snapshot, status }
}
