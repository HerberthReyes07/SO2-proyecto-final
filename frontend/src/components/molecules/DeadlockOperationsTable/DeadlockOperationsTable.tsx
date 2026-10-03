import { Badge } from '@/components/atoms/Badge'
import { RESOURCE_LABELS } from '@/constants/resourceLabels'
import type { DeadlockedOperation, OperationKind, ResourceType } from '@/types/simulation'
import styles from './DeadlockOperationsTable.module.css'

export interface DeadlockOperationsTableProps {
  operations: readonly DeadlockedOperation[]
  /** Recursos disputados: se resaltan entre lo que cada operación retiene. */
  contested: readonly ResourceType[]
}

const KIND_LABELS: Record<OperationKind, string> = {
  ORDER: 'Pedido',
  TRUCK: 'Camión',
  OTHER: 'Operación',
}

/** Las operaciones bloqueadas: qué retiene cada una y qué espera sin poder recibirlo. */
export function DeadlockOperationsTable({ operations, contested }: DeadlockOperationsTableProps) {
  return (
    <div className={styles.wrapper}>
      <table className={styles.table}>
        <thead>
          <tr>
            <th scope="col">Operación</th>
            <th scope="col">Retiene</th>
            <th scope="col">Espera</th>
          </tr>
        </thead>
        <tbody>
          {operations.map((operation) => (
            <tr key={operation.id}>
              <th scope="row">
                <span className={styles.id}>{operation.id}</span>
                <span className={styles.kind}>{KIND_LABELS[operation.kind]}</span>
              </th>
              <td>
                <div className={styles.badges}>
                  {operation.held.map((resource) => (
                    <Badge key={resource} tone={contested.includes(resource) ? 'danger' : 'neutral'}>
                      {RESOURCE_LABELS[resource]}
                    </Badge>
                  ))}
                </div>
              </td>
              <td>
                <Badge tone="warning">{RESOURCE_LABELS[operation.waitingFor]}</Badge>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
