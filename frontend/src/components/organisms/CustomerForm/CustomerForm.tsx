import { useState, type FormEvent } from 'react'
import { Button } from '@/components/atoms/Button'
import { Input } from '@/components/atoms/Input'
import { Select, type SelectOption } from '@/components/atoms/Select'
import { FormField } from '@/components/molecules/FormField'
import type { CreateCustomerRequest, ServiceLevelId } from '@/types/catalog'
import styles from './CustomerForm.module.css'

export interface CustomerFormProps {
  serviceLevelOptions: readonly SelectOption[]
  /** Envía la cuenta nueva; devuelve true si el servidor la aceptó. */
  onSubmit: (request: CreateCustomerRequest) => Promise<boolean>
  pending: boolean
  error?: string | null
  /** Mensaje de éxito de la última cuenta creada. */
  confirmation?: string | null
}

/** Alta de un cliente nuevo: nombre y nivel de servicio que desea recibir. */
export function CustomerForm({ serviceLevelOptions, onSubmit, pending, error, confirmation }: CustomerFormProps) {
  const [name, setName] = useState('')
  const [serviceLevel, setServiceLevel] = useState<ServiceLevelId | ''>('')
  const canSubmit = name.trim() !== '' && serviceLevel !== '' && !pending

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    if (serviceLevel === '') return
    const accepted = await onSubmit({ name: name.trim(), serviceLevel })
    if (accepted) {
      setName('')
      setServiceLevel('')
    }
  }

  return (
    <form className={styles.form} onSubmit={handleSubmit} aria-labelledby="customer-form-title">
      <h2 id="customer-form-title" className={styles.title}>
        Cliente nuevo
      </h2>
      <FormField label="Nombre del cliente" htmlFor="customer-name">
        <Input id="customer-name" value={name} maxLength={60} onChange={(event) => setName(event.target.value)} />
      </FormField>
      <FormField label="Nivel de servicio" htmlFor="customer-level">
        <Select
          id="customer-level"
          options={serviceLevelOptions}
          placeholder="Seleccione un nivel"
          value={serviceLevel}
          onChange={(event) => setServiceLevel(event.target.value as ServiceLevelId)}
        />
      </FormField>
      <Button type="submit" variant="primary" disabled={!canSubmit}>
        {pending ? 'Creando…' : 'Crear cuenta'}
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
