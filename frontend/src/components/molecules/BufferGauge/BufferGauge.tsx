import { Badge } from '@/components/atoms/Badge'
import { ProgressBar } from '@/components/atoms/ProgressBar'
import type { ReceptionBufferState } from '@/types/simulation'
import styles from './BufferGauge.module.css'

export interface BufferGaugeProps {
  buffer: ReceptionBufferState
}

/**
 * Estado del buffer entre Recepción e Inventario: cuánto se ha llenado y, si lo está, cuántos
 * camiones esperan espacio (contrapresión: Recepción va más rápido que Inventario). Nada se pierde:
 * esperan, no se descartan.
 */
export function BufferGauge({ buffer }: BufferGaugeProps) {
  const backpressure = buffer.blockedProducers > 0

  return (
    <div className={styles.gauge}>
      <div className={styles.header}>
        <span className={styles.title}>Buffer Recepción → Inventario</span>
        {backpressure && <Badge tone="warning">Contrapresión: {buffer.blockedProducers} esperando espacio</Badge>}
      </div>
      <ProgressBar value={buffer.size} max={buffer.capacity} label="Ocupación del buffer de recepción" />
      <p className={styles.counts}>
        {buffer.size} / {buffer.capacity} mensajes · {buffer.accepted} recibidos · {buffer.delivered} procesados
      </p>
    </div>
  )
}
