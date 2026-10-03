import { Badge } from '@/components/atoms/Badge'
import type { Lot } from '@/types/simulation'
import styles from './LotTable.module.css'

export interface LotTableProps {
  lots: readonly Lot[]
  /** Diccionario id de producto → nombre mostrado. */
  productNames: Record<string, string>
  highlightedLotId: string | null
  /** Se invoca con el id del lote al apuntarlo (o enfocarlo) y con null al dejarlo. */
  onHighlight: (lotId: string | null) => void
}

/**
 * Los lotes y cuántas ubicaciones ocupan. Un lote que ocupa un solo tramo contiguo está "junto"; si
 * está repartido en varios tramos está "disperso". Apuntar un lote resalta sus ubicaciones en el mapa.
 */
export function LotTable({ lots, productNames, highlightedLotId, onHighlight }: LotTableProps) {
  return (
    <div className={styles.wrapper}>
      <table className={styles.table}>
        <thead>
          <tr>
            <th scope="col">Lote</th>
            <th scope="col">Producto</th>
            <th scope="col">Unidades</th>
            <th scope="col">Ubicaciones</th>
            <th scope="col">Distribución</th>
          </tr>
        </thead>
        <tbody>
          {lots.map((lot) => (
            <tr
              key={lot.lotId}
              className={lot.lotId === highlightedLotId ? styles.highlighted : undefined}
              tabIndex={0}
              onMouseEnter={() => onHighlight(lot.lotId)}
              onMouseLeave={() => onHighlight(null)}
              onFocus={() => onHighlight(lot.lotId)}
              onBlur={() => onHighlight(null)}
            >
              <th scope="row">{lot.lotId}</th>
              <td>{productNames[lot.productId] ?? lot.productId}</td>
              <td>{lot.units}</td>
              <td>{lot.locations.length}</td>
              <td>
                {lot.runs <= 1 ? (
                  <Badge tone="success">Junto</Badge>
                ) : (
                  <Badge tone="warning">Disperso en {lot.runs} tramos</Badge>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
