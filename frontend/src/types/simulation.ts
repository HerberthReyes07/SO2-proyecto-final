import type { PolicyId, ServiceLevelId } from '@/types/catalog'

/** Tipos que reflejan el contrato JSON del backend (record `SimulationSnapshot` y relacionados). */

export type ResourceType =
  | 'WAREHOUSE_WORKER'
  | 'FORKLIFT'
  | 'QUALITY_STATION'
  | 'PACKING_STATION'
  | 'SCANNER'
  | 'LOADING_DOCK'

/** Foto de un pool de recursos compartidos. */
export interface PoolSnapshot {
  type: ResourceType
  capacity: number
  /** Ids de las operaciones que retienen una unidad. */
  holders: string[]
  /** Ids de las operaciones en espera, en el orden en que se les concederá el recurso. */
  waiting: string[]
  /** Ocupación promedio desde el último reinicio de estadísticas, entre 0 y 1. */
  utilization: number
}

/** Etapas por las que pasa un pedido, en orden. */
export type Stage = 'PICKING' | 'INSPECTION' | 'PACKING' | 'DISPATCH'

export type OrderStatus = 'WAITING' | 'PROCESSING' | 'COMPLETED'

/** Avance de un pedido en proceso. */
export interface OrderProgress {
  orderId: string
  customerName: string
  serviceLevel: ServiceLevelId
  totalItems: number
  status: OrderStatus
  stage: Stage
  /** Recurso que el pedido está esperando, o null si no espera ninguno. */
  awaiting: ResourceType | null
  /** Recursos que retiene en este momento. */
  held: ResourceType[]
  /** Avance del trabajo, entre 0 y 1. */
  progress: number
  /** Tiempo simulado de trabajo restante, en ms (no incluye esperas futuras por recursos). */
  remainingMillis: number
}

/** Faltante de un producto para poder atender un pedido. */
export interface MissingStock {
  productId: string
  /** Unidades que faltan (siempre positivo). */
  missingUnits: number
}

/** Un pedido en la lista de espera. */
export interface WaitingOrder {
  orderId: string
  customerName: string
  serviceLevel: ServiceLevelId
  totalItems: number
  /** Tiempo simulado que lleva esperando, en ms. */
  waitedMillis: number
  /** Tiempo máximo de espera objetivo de su nivel, en ms; si se supera, el pedido está retrasado. */
  targetWaitMillis: number
  /** Productos de los que falta stock; mientras no esté vacío el pedido no puede admitirse. */
  missingStock: MissingStock[]
}

/** Política vigente y lista de espera. */
export interface SchedulingSnapshot {
  policy: PolicyId
  /** Total de pedidos en espera (la lista `waiting` puede mostrar solo los primeros). */
  waitingTotal: number
  /** Los primeros pedidos en espera, en el orden en que se atenderán. */
  waiting: WaitingOrder[]
}

/** Foto del reloj simulado. */
export interface ClockSnapshot {
  /** Milisegundos simulados transcurridos desde el inicio de la simulación. */
  simulatedMillis: number
  /** Factor de aceleración (60 = 1 s real equivale a 1 min simulado). */
  speed: number
  paused: boolean
}

/** Estado del generador automático de pedidos. */
export interface GeneratorState {
  enabled: boolean
  /** Intensidad: pedidos por minuto simulado. */
  ordersPerMinute: number
  /** Semilla vigente; anotarla permite repetir la misma secuencia de pedidos. */
  seed: number
  /** Pedidos generados desde que arrancó la aplicación. */
  generated: number
}

/** Estadísticas de los pedidos despachados de un nivel de servicio (tiempos simulados, en ms). */
export interface LevelStats {
  level: ServiceLevelId
  completed: number
  avgWaitMillis: number
  maxWaitMillis: number
  avgProcessingMillis: number
  /** Fracción de pedidos atendidos dentro del tiempo objetivo de su nivel, entre 0 y 1. */
  onTimeRatio: number
}

/** Estadísticas generales de todos los niveles juntos. */
export interface StatsTotals {
  completed: number
  avgWaitMillis: number
  avgProcessingMillis: number
  onTimeRatio: number
  throughputPerMinute: number
  /** Tiempo simulado transcurrido desde el último reinicio, en ms. */
  elapsedMillis: number
}

export interface StatsSnapshot {
  totals: StatsTotals
  levels: LevelStats[]
}

/** Existencias de un producto. */
export interface StockItem {
  productId: string
  /** Unidades libres, que pueden reservarse para nuevos pedidos. */
  available: number
  /** Unidades apartadas para pedidos en proceso que aún no se despachan. */
  reserved: number
}

/** Estado del buffer entre Recepción e Inventario. */
export interface ReceptionBufferState {
  capacity: number
  size: number
  /** Camiones esperando espacio: Recepción va más rápido que Inventario (contrapresión). */
  blockedProducers: number
  accepted: number
  delivered: number
}

/** Mensaje con el que Recepción informa a Inventario qué mercadería llegó. */
export interface ReceptionMessage {
  truckId: string
  productId: string
  quantity: number
}

export interface InventoryWorkerState {
  /** Mensaje que Inventario procesa ahora, o null si está ocioso. */
  processing: ReceptionMessage | null
  processed: number
}

export interface InventoryState {
  stock: StockItem[]
  buffer: ReceptionBufferState
  worker: InventoryWorkerState
}

export type TruckPhase = 'WAITING_RESOURCES' | 'UNLOADING' | 'REPORTING'

/** Un camión en Recepción. */
export interface Truck {
  truckId: string
  productId: string
  quantity: number
  phase: TruckPhase
  awaiting: ResourceType | null
  held: ResourceType[]
  /** Avance de la descarga, entre 0 y 1. */
  progress: number
}

export interface ReceptionState {
  trucks: Truck[]
}

/** Estado completo del centro de distribución. */
export interface SimulationSnapshot {
  clock: ClockSnapshot
  scheduling: SchedulingSnapshot
  generator: GeneratorState
  stats: StatsSnapshot
  inventory: InventoryState
  reception: ReceptionState
  resources: PoolSnapshot[]
  activeOrders: OrderProgress[]
  completedOrders: number
}

/** Estado de la conexión en vivo con el backend. */
export type ConnectionStatus = 'connecting' | 'open' | 'closed'
