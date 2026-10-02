import type { ResourceType } from '@/types/simulation'

/** Tipos que reflejan los datos de referencia y los formularios del backend. */

export type ServiceLevelId = 'EXPRESS' | 'PRIORITY' | 'STANDARD' | 'SCHEDULED' | 'ECONOMY'

export type PolicyId =
  | 'FIFO'
  | 'STRICT_PRIORITY'
  | 'AGING_PRIORITY'
  | 'EARLIEST_DEADLINE_FIRST'
  | 'SHORTEST_JOB_FIRST'

export interface Product {
  id: string
  name: string
  /** Id del tipo de mercancía (ver `MerchandiseTypeInfo.id`). */
  type: string
  unitVolume: number
}

export interface MerchandiseTypeInfo {
  id: string
  displayName: string
  description: string
  /** Recursos que puede requerir, en orden global de adquisición. */
  recipe: ResourceType[]
}

export interface ServiceLevelInfo {
  id: ServiceLevelId
  level: number
  displayName: string
  /** Tiempo máximo de espera objetivo, en minutos simulados. */
  targetWaitMinutes: number
}

/** Política de atención con la explicación de su criterio de orden. */
export interface PolicyInfo {
  id: PolicyId
  displayName: string
  description: string
}

export interface CatalogResponse {
  products: Product[]
  merchandiseTypes: MerchandiseTypeInfo[]
  serviceLevels: ServiceLevelInfo[]
  policies: PolicyInfo[]
}

export interface Customer {
  id: string
  name: string
  level: ServiceLevelId
}

export interface CreateCustomerRequest {
  name: string
  serviceLevel: ServiceLevelId
}

export interface OrderLineRequest {
  productId: string
  quantity: number
}

export interface PlaceOrderRequest {
  customerId: string
  lines: OrderLineRequest[]
}

export interface OrderConfirmation {
  orderId: string
  totalItems: number
}
