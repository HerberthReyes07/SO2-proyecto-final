import type { DeadlockReport } from '@/types/simulation'
import { explainConflict } from './explainConflict'

const report = (overrides: Partial<DeadlockReport> = {}): DeadlockReport => ({
  id: 'DL-0001',
  detectedAtMillis: 0,
  operations: [
    { id: 'ORD-0001', kind: 'ORDER', held: ['WAREHOUSE_WORKER', 'SCANNER'], waitingFor: 'LOADING_DOCK' },
    { id: 'TRK-0001', kind: 'TRUCK', held: ['LOADING_DOCK'], waitingFor: 'SCANNER' },
  ],
  resources: ['SCANNER', 'LOADING_DOCK'],
  conflict: { waiter: 'ORD-0001', holder: 'TRK-0001' },
  ...overrides,
})

describe('explainConflict', () => {
  it('explains the classic case where the two operations wait for each other', () => {
    expect(explainConflict(report())).toEqual([
      'ORD-0001 espera Áreas de carga, que retiene TRK-0001.',
      'TRK-0001 espera Sistema de escaneo, que retiene ORD-0001.',
      'Se esperan mutuamente: ninguna puede avanzar mientras la otra siga reteniendo lo que necesita.',
    ])
  })

  it('explains a cycle that goes through a third operation', () => {
    const threeWay = report({
      operations: [
        { id: 'A', kind: 'OTHER', held: ['SCANNER'], waitingFor: 'LOADING_DOCK' },
        { id: 'B', kind: 'OTHER', held: ['LOADING_DOCK'], waitingFor: 'FORKLIFT' },
        { id: 'C', kind: 'OTHER', held: ['FORKLIFT'], waitingFor: 'SCANNER' },
      ],
      conflict: { waiter: 'A', holder: 'B' },
    })

    expect(explainConflict(threeWay)).toEqual([
      'A espera Áreas de carga, que retiene B.',
      'B a su vez espera Montacargas, y así se cierra un ciclo de espera.',
      'Ninguna puede avanzar: cada una espera algo que retiene otra del ciclo.',
    ])
  })

  it('says nothing when the operations of the conflict are missing from the report', () => {
    expect(explainConflict(report({ conflict: { waiter: 'X', holder: 'Y' } }))).toEqual([])
  })
})
