import type { CatalogResponse, Customer, ServiceLevelInfo } from '@/types/catalog'
import { buildCustomerOptions, buildProductOptions, buildServiceLevelOptions, describeTargetWait } from './options'

const express: ServiceLevelInfo = { id: 'EXPRESS', level: 1, displayName: 'Exprés', targetWaitMinutes: 1 }
const priority: ServiceLevelInfo = { id: 'PRIORITY', level: 2, displayName: 'Prioritario', targetWaitMinutes: 10 }

describe('describeTargetWait', () => {
  it('calls the express level almost instantaneous and the others by their wait', () => {
    expect(describeTargetWait(express)).toBe('casi instantáneo')
    expect(describeTargetWait(priority)).toBe('espera máx. 10 min')
  })
})

describe('buildServiceLevelOptions', () => {
  it('shows level number, name and target wait', () => {
    expect(buildServiceLevelOptions([priority])).toEqual([
      { value: 'PRIORITY', label: 'Nivel 2 · Prioritario (espera máx. 10 min)' },
    ])
  })
})

describe('buildProductOptions', () => {
  const catalog: CatalogResponse = {
    products: [
      { id: 'P-007', name: 'Refrigeradora', type: 'HEAVY', unitVolume: 60 },
      { id: 'P-999', name: 'Misterio', type: 'UNKNOWN', unitVolume: 1 },
    ],
    merchandiseTypes: [{ id: 'HEAVY', displayName: 'Pesado', description: '', recipe: [] }],
    serviceLevels: [],
    policies: [],
  }

  it('appends the merchandise type name to each product', () => {
    expect(buildProductOptions(catalog)[0]).toEqual({ value: 'P-007', label: 'Refrigeradora · Pesado' })
  })

  it('falls back to the type id when the type is unknown', () => {
    expect(buildProductOptions(catalog)[1].label).toBe('Misterio · UNKNOWN')
  })
})

describe('buildCustomerOptions', () => {
  it('appends the service level name to each customer', () => {
    const customers: Customer[] = [{ id: 'CUS-001', name: 'Aurora Tech', level: 'EXPRESS' }]

    expect(buildCustomerOptions(customers, [express])).toEqual([{ value: 'CUS-001', label: 'Aurora Tech · Exprés' }])
  })
})
