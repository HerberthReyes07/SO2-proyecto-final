import { useState, type FormEvent } from 'react'
import { Button } from '@/components/atoms/Button'
import { Input } from '@/components/atoms/Input'
import { Select, type SelectOption } from '@/components/atoms/Select'
import { FormField } from '@/components/molecules/FormField'
import type { RegisterArrivalRequest } from '@/types/catalog'
import { validateArrival } from '@/utils/arrivalDraft'
import styles from './ReceptionForm.module.css'

export interface ReceptionFormProps {
  productOptions: readonly SelectOption[]
  /** Envía la llegada; devuelve true si el servidor la aceptó. */
  onSubmit: (request: RegisterArrivalRequest) => Promise<boolean>
  pending: boolean
  error?: string | null
  /** Mensaje de éxito de la última llegada registrada. */
  confirmation?: string | null
}

const DEFAULT_QUANTITY = 100

/** Registro manual de la llegada de mercadería: producto, unidades por camión y cuántos camiones. */
export function ReceptionForm({ productOptions, onSubmit, pending, error, confirmation }: ReceptionFormProps) {
  const [productId, setProductId] = useState('')
  const [quantity, setQuantity] = useState(DEFAULT_QUANTITY)
  const [trucks, setTrucks] = useState(1)

  const validationMessage = validateArrival(productId, quantity, trucks)
  const canSubmit = validationMessage === null && !pending

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    if (validationMessage !== null) return
    await onSubmit({ productId, quantity, trucks })
  }

  return (
    <form className={styles.form} onSubmit={handleSubmit} aria-labelledby="reception-form-title">
      <h2 id="reception-form-title" className={styles.title}>
        Registrar llegada de mercadería
      </h2>

      <FormField label="Producto recibido" htmlFor="arrival-product">
        <Select
          id="arrival-product"
          options={productOptions}
          placeholder="Seleccione un producto"
          value={productId}
          onChange={(event) => setProductId(event.target.value)}
        />
      </FormField>

      <div className={styles.numbers}>
        <FormField label="Unidades por camión" htmlFor="arrival-quantity">
          <Input
            id="arrival-quantity"
            type="number"
            min={1}
            step={1}
            value={Number.isNaN(quantity) ? '' : quantity}
            onChange={(event) => setQuantity(event.target.valueAsNumber)}
          />
        </FormField>
        <FormField label="Camiones" htmlFor="arrival-trucks">
          <Input
            id="arrival-trucks"
            type="number"
            min={1}
            step={1}
            value={Number.isNaN(trucks) ? '' : trucks}
            onChange={(event) => setTrucks(event.target.valueAsNumber)}
          />
        </FormField>
      </div>
      <p className={styles.hint}>
        Varios camiones juntos permiten provocar una ráfaga y ver cómo se llena el buffer de Inventario.
      </p>

      {validationMessage && productId !== '' && <p className={styles.hint}>{validationMessage}</p>}

      <Button type="submit" variant="primary" disabled={!canSubmit}>
        {pending ? 'Registrando…' : 'Registrar llegada'}
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
