import { BufferGauge } from '@/components/molecules/BufferGauge'
import { TruckItem } from '@/components/molecules/TruckItem'
import type { InventoryState, ReceptionState } from '@/types/simulation'
import styles from './ReceptionPanel.module.css'

export interface ReceptionPanelProps {
  reception: ReceptionState
  inventory: InventoryState
  /** Diccionario id de producto → nombre mostrado. */
  productNames: Record<string, string>
}

/**
 * Recepción de mercadería e Inventario, las dos áreas independientes, con lo único que las
 * comunica: el buffer. Muestra los camiones, el buffer y qué está procesando Inventario.
 */
export function ReceptionPanel({ reception, inventory, productNames }: ReceptionPanelProps) {
  const processing = inventory.worker.processing
  const nameOf = (productId: string) => productNames[productId] ?? productId

  return (
    <section aria-labelledby="reception-title">
      <h2 id="reception-title" className={styles.title}>
        Recepción e Inventario
      </h2>

      <BufferGauge buffer={inventory.buffer} />
      <p className={styles.worker}>
        {processing
          ? `Inventario procesa ${processing.truckId}: ${nameOf(processing.productId)} × ${processing.quantity}`
          : 'Inventario está ocioso.'}
      </p>

      <h3 className={styles.subtitle}>Camiones en recepción</h3>
      {reception.trucks.length === 0 ? (
        <p className={styles.empty}>No hay camiones en recepción. Registre una llegada de mercadería desde el formulario.</p>
      ) : (
        <ul className={styles.list}>
          {reception.trucks.map((truck) => (
            <TruckItem key={truck.truckId} truck={truck} productName={nameOf(truck.productId)} />
          ))}
        </ul>
      )}
    </section>
  )
}
