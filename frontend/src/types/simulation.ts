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
}

/** Foto del reloj simulado. */
export interface ClockSnapshot {
  /** Milisegundos simulados transcurridos desde el inicio de la simulación. */
  simulatedMillis: number
  /** Factor de aceleración (60 = 1 s real equivale a 1 min simulado). */
  speed: number
  paused: boolean
}

/** Estado completo del centro de distribución. */
export interface SimulationSnapshot {
  clock: ClockSnapshot
  resources: PoolSnapshot[]
}

/** Estado de la conexión en vivo con el backend. */
export type ConnectionStatus = 'connecting' | 'open' | 'closed'
