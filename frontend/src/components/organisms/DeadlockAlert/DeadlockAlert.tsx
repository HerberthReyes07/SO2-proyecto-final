import { Badge } from '@/components/atoms/Badge'
import { Button } from '@/components/atoms/Button'
import { CoffmanChecklist, type CoffmanItem } from '@/components/molecules/CoffmanChecklist'
import { DeadlockOperationsTable } from '@/components/molecules/DeadlockOperationsTable'
import { STATIC_COFFMAN_CONDITIONS } from '@/constants/coffmanConditions'
import { RESOURCE_LABELS } from '@/constants/resourceLabels'
import type { DeadlockReport } from '@/types/simulation'
import { explainConflict } from '@/utils/explainConflict'
import styles from './DeadlockAlert.module.css'

export interface DeadlockAlertProps {
  report: DeadlockReport
  /** Resuelve el deadlock dejando continuar a la operación indicada; la otra vuelve a la lista de espera. */
  onResolve: (continueOperationId: string) => void
  /** Hay una resolución en curso: se deshabilitan los botones para no resolver dos veces. */
  pending: boolean
}

/**
 * Un deadlock confirmado: qué operaciones están bloqueadas, qué recursos se disputan, por qué (las cuatro
 * condiciones de Coffman) y cómo resolverlo eligiendo cuál de las dos operaciones en conflicto continúa.
 */
export function DeadlockAlert({ report, onResolve, pending }: DeadlockAlertProps) {
  const { waiter, holder } = report.conflict
  const explanation = explainConflict(report)
  const conditions: CoffmanItem[] = [
    ...STATIC_COFFMAN_CONDITIONS,
    { id: 'circular-wait', name: 'Espera circular', explanation: explanation.slice(0, 2).join(' ') },
  ]

  return (
    <section className={styles.alert} aria-labelledby="deadlock-alert-title">
      <h3 id="deadlock-alert-title" className={styles.title}>
        Deadlock detectado ({report.id})
      </h3>

      <div className={styles.explanation}>
        {explanation.map((sentence) => (
          <p key={sentence}>{sentence}</p>
        ))}
      </div>

      <h4 className={styles.subtitle}>Operaciones bloqueadas</h4>
      <DeadlockOperationsTable operations={report.operations} contested={report.resources} />
      <p className={styles.contested}>
        Recursos disputados:{' '}
        {report.resources.map((resource) => (
          <Badge key={resource} tone="danger">
            {RESOURCE_LABELS[resource]}
          </Badge>
        ))}
      </p>

      <h4 className={styles.subtitle}>Por qué ocurre: las cuatro condiciones de Coffman</h4>
      <CoffmanChecklist conditions={conditions} />

      <h4 className={styles.subtitle}>Resolver</h4>
      <p className={styles.help}>
        Elija cuál de las dos operaciones en conflicto continúa con los recursos. La otra libera todo lo que
        retiene y vuelve a la lista de espera para atenderse más adelante.
      </p>
      <div className={styles.actions}>
        <Button variant="primary" disabled={pending} onClick={() => onResolve(waiter)}>
          Que continúe {waiter}
        </Button>
        <Button variant="primary" disabled={pending} onClick={() => onResolve(holder)}>
          Que continúe {holder}
        </Button>
      </div>
      <p className={styles.help}>
        Si continúa {waiter}, vuelve a la lista de espera {holder}; si continúa {holder}, vuelve {waiter}.
      </p>
    </section>
  )
}
