import type { PlacementStrategyId, PolicyId, PreventionModeId, ServiceLevelId } from '@/types/catalog'

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
  /** Veces que fue expulsado para deshacer un deadlock y devuelto a la lista de espera. */
  retries: number
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
  /** Veces que fue expulsado para deshacer un deadlock y devuelto a la lista. */
  retries: number
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

export type TruckPhase = 'WAITING_RESOURCES' | 'MANEUVERING' | 'UNLOADING' | 'REPORTING' | 'RETRYING'

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
  /** Veces que fue expulsado para deshacer un deadlock. */
  retries: number
}

export interface ReceptionState {
  trucks: Truck[]
}

/** Geometría del almacén: pasillos × posiciones × niveles, todas las ubicaciones del mismo tamaño. */
export interface WarehouseLayout {
  aisles: number
  positions: number
  levels: number
  /** Capacidad de cada ubicación, en unidades de volumen. */
  locationCapacity: number
}

/** Lo que hay en una ubicación ocupada; una ubicación pertenece a un solo lote. */
export interface LocationContent {
  lotId: string
  productId: string
  units: number
  /** Volumen que ocupan las unidades; lo que falta hasta la capacidad es fragmentación interna. */
  usedVolume: number
}

/** Un lote y dónde está guardado. */
export interface Lot {
  lotId: string
  productId: string
  units: number
  /** Índices de las ubicaciones que ocupa, de menor a mayor. */
  locations: number[]
  /** Tramos contiguos en que está repartido (1 = todo junto). */
  runs: number
}

export interface WarehouseState {
  layout: WarehouseLayout
  strategy: PlacementStrategyId
  usedLocations: number
  totalLocations: number
  /** Volumen perdido por fragmentación interna. */
  wastedVolume: number
  /** Unidades rechazadas desde el inicio por no haber ubicaciones libres. */
  rejectedUnits: number
  /** Contenido de cada ubicación según su índice; null si está libre. */
  locations: (LocationContent | null)[]
  lots: Lot[]
}

/** Clase de operación que compite por recursos. */
export type OperationKind = 'ORDER' | 'TRUCK' | 'OTHER'

/** Una operación bloqueada en un deadlock. */
export interface DeadlockedOperation {
  id: string
  kind: OperationKind
  /** Recursos que retiene. */
  held: ResourceType[]
  /** Recurso que espera y no recibirá mientras dure el deadlock. */
  waitingFor: ResourceType
}

/** Las dos operaciones entre las que se elige cuál continúa: `waiter` espera lo que retiene `holder`. */
export interface ConflictPair {
  waiter: string
  holder: string
}

/** Un deadlock confirmado. */
export interface DeadlockReport {
  id: string
  /** Tiempo simulado en que se confirmó, en ms. */
  detectedAtMillis: number
  operations: DeadlockedOperation[]
  /** Recursos disputados: los que las operaciones bloqueadas esperan. */
  resources: ResourceType[]
  conflict: ConflictPair
}

export interface DeadlockState {
  prevention: PreventionModeId
  /** El deadlock confirmado ahora, o null si no hay ninguno. */
  current: DeadlockReport | null
  detected: number
  resolved: number
}

/** En qué punto está el escenario de demostración del deadlock. */
export type ScenarioPhase = 'IDLE' | 'RUNNING' | 'FINISHED_CLEAN' | 'FINISHED_WITH_DEADLOCK'

export interface ScenarioState {
  phase: ScenarioPhase
  /** Prevención con la que se lanzó el último escenario, o null si nunca se lanzó. */
  mode: PreventionModeId | null
}

/** Estado completo del centro de distribución. */
export interface SimulationSnapshot {
  clock: ClockSnapshot
  scheduling: SchedulingSnapshot
  generator: GeneratorState
  stats: StatsSnapshot
  inventory: InventoryState
  reception: ReceptionState
  warehouse: WarehouseState
  deadlock: DeadlockState
  scenario: ScenarioState
  resources: PoolSnapshot[]
  activeOrders: OrderProgress[]
  completedOrders: number
}

/** Estado de la conexión en vivo con el backend. */
export type ConnectionStatus = 'connecting' | 'open' | 'closed'
