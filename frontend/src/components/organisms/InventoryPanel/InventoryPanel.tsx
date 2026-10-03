import { StockTable } from '@/components/molecules/StockTable'
import type { StockItem } from '@/types/simulation'
import styles from './InventoryPanel.module.css'

export interface InventoryPanelProps {
  stock: readonly StockItem[]
  /** Diccionario id de producto → nombre mostrado. */
  productNames: Record<string, string>
}

/** Existencias de todos los productos del catálogo. */
export function InventoryPanel({ stock, productNames }: InventoryPanelProps) {
  return (
    <section aria-labelledby="inventory-title">
      <h2 id="inventory-title" className={styles.title}>
        Inventario
      </h2>
      <StockTable stock={stock} productNames={productNames} />
    </section>
  )
}
