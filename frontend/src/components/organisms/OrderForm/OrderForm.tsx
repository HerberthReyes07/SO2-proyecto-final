import { useRef, useState, type FormEvent } from 'react'
import { Button } from '@/components/atoms/Button'
import type { SelectOption } from '@/components/atoms/Select'
import { Select } from '@/components/atoms/Select'
import { FormField } from '@/components/molecules/FormField'
import { OrderLineRow } from '@/components/molecules/OrderLineRow'
import { MAX_ORDER_LINES } from '@/constants/orderLimits'
import type { PlaceOrderRequest } from '@/types/catalog'
import {
  createEmptyLine,
  toPlaceOrderRequest,
  totalUnits,
  validateDraft,
  type OrderLineDraft,
} from '@/utils/orderDraft'
import styles from './OrderForm.module.css'

export interface OrderFormProps {
  customerOptions: readonly SelectOption[]
  productOptions: readonly SelectOption[]
  /** Envía el pedido; devuelve true si el servidor lo aceptó. */
  onSubmit: (request: PlaceOrderRequest) => Promise<boolean>
  pending: boolean
  error?: string | null
  /** Mensaje de éxito del último pedido registrado. */
  confirmation?: string | null
}

/** Registro manual de un pedido: cliente y una o más líneas de producto con su cantidad. */
export function OrderForm({ customerOptions, productOptions, onSubmit, pending, error, confirmation }: OrderFormProps) {
  const [customerId, setCustomerId] = useState('')
  const nextKey = useRef(1)
  const [lines, setLines] = useState<OrderLineDraft[]>(() => [createEmptyLine(0)])

  const validationMessage = validateDraft(customerId, lines)
  const canSubmit = validationMessage === null && !pending

  const updateLine = (updated: OrderLineDraft) =>
    setLines((current) => current.map((line) => (line.key === updated.key ? updated : line)))
  const removeLine = (key: number) => setLines((current) => current.filter((line) => line.key !== key))
  const addLine = () => setLines((current) => [...current, createEmptyLine(nextKey.current++)])

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    if (validationMessage !== null) return
    const accepted = await onSubmit(toPlaceOrderRequest(customerId, lines))
    // El cliente se conserva: es común registrar varios pedidos seguidos del mismo cliente.
    if (accepted) setLines([createEmptyLine(nextKey.current++)])
  }

  return (
    <form className={styles.form} onSubmit={handleSubmit} aria-labelledby="order-form-title">
      <h2 id="order-form-title" className={styles.title}>
        Registrar pedido
      </h2>

      <FormField label="Cliente" htmlFor="order-customer">
        <Select
          id="order-customer"
          options={customerOptions}
          placeholder="Seleccione un cliente"
          value={customerId}
          onChange={(event) => setCustomerId(event.target.value)}
        />
      </FormField>

      <div className={styles.lines}>
        {lines.map((line, index) => (
          <OrderLineRow
            key={line.key}
            line={line}
            position={index + 1}
            productOptions={productOptions}
            removable={lines.length > 1}
            onChange={updateLine}
            onRemove={() => removeLine(line.key)}
          />
        ))}
      </div>

      <div className={styles.actions}>
        <Button disabled={lines.length >= MAX_ORDER_LINES} onClick={addLine}>
          + Agregar producto
        </Button>
        <span className={styles.total}>Total: {totalUnits(lines)} artículos</span>
      </div>

      {validationMessage && <p className={styles.hint}>{validationMessage}</p>}

      <Button type="submit" variant="primary" disabled={!canSubmit}>
        {pending ? 'Registrando…' : 'Registrar pedido'}
      </Button>

      {error && (
        <p className={styles.error} role="alert">
          {error}
        </p>
      )}
      {confirmation && (
        <p className={styles.success} role="status">
          {confirmation}
        </p>
      )}
    </form>
  )
}
