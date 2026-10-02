import { Button } from '@/components/atoms/Button'
import { Input } from '@/components/atoms/Input'
import { Select, type SelectOption } from '@/components/atoms/Select'
import { MAX_QUANTITY_PER_LINE } from '@/constants/orderLimits'
import type { OrderLineDraft } from '@/utils/orderDraft'
import styles from './OrderLineRow.module.css'

export interface OrderLineRowProps {
  line: OrderLineDraft
  /** Posición de la fila (desde 1), solo para nombrar los controles de forma accesible. */
  position: number
  productOptions: readonly SelectOption[]
  removable: boolean
  onChange: (line: OrderLineDraft) => void
  onRemove: () => void
}

/** Una línea del pedido: producto, cantidad y botón para quitarla. */
export function OrderLineRow({ line, position, productOptions, removable, onChange, onRemove }: OrderLineRowProps) {
  return (
    <div className={styles.row}>
      <Select
        aria-label={`Producto ${position}`}
        options={productOptions}
        placeholder="Seleccione un producto"
        value={line.productId}
        onChange={(event) => onChange({ ...line, productId: event.target.value })}
      />
      <div className={styles.quantity}>
        <Input
          aria-label={`Cantidad ${position}`}
          type="number"
          min={1}
          max={MAX_QUANTITY_PER_LINE}
          step={1}
          value={Number.isNaN(line.quantity) ? '' : line.quantity}
          onChange={(event) => onChange({ ...line, quantity: event.target.valueAsNumber })}
        />
      </div>
      <Button aria-label={`Quitar línea ${position}`} disabled={!removable} onClick={onRemove}>
        ✕
      </Button>
    </div>
  )
}
