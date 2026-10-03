import { Badge } from '@/components/atoms/Badge'
import type { StockItem } from '@/types/simulation'
import { getStockStatus } from '@/utils/stockStatus'
import styles from './StockTable.module.css'

export interface StockTableProps {
  stock: readonly StockItem[]
  /** Diccionario id de producto → nombre mostrado. */
  productNames: Record<string, string>
}

/** Existencias de cada producto: disponibles, reservadas para pedidos en proceso y un aviso si escasean. */
export function StockTable({ stock, productNames }: StockTableProps) {
  return (
    <div className={styles.wrapper}>
      <table className={styles.table}>
        <thead>
          <tr>
            <th scope="col">Producto</th>
            <th scope="col">Disponible</th>
            <th scope="col">Reservado</th>
            <th scope="col">Estado</th>
          </tr>
        </thead>
        <tbody>
          {stock.map((item) => {
            const status = getStockStatus(item.available)
            return (
              <tr key={item.productId}>
                <th scope="row">{productNames[item.productId] ?? item.productId}</th>
                <td>{item.available}</td>
                <td>{item.reserved}</td>
                <td>{status ? <Badge tone={status.tone}>{status.label}</Badge> : null}</td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
