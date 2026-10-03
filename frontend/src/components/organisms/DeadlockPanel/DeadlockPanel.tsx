import { Badge } from '@/components/atoms/Badge'
import { Button } from '@/components/atoms/Button'
import { DescribedSelect } from '@/components/molecules/DescribedSelect'
import { ResourceCard } from '@/components/molecules/ResourceCard'
import { DeadlockAlert } from '@/components/organisms/DeadlockAlert'
import type { PreventionInfo, PreventionModeId } from '@/types/catalog'
import { RESOURCE_LABELS } from '@/constants/resourceLabels'
import type { DeadlockState, PoolSnapshot, ScenarioState } from '@/types/simulation'
import { describeScenario } from '@/utils/describeScenario'
import styles from './DeadlockPanel.module.css'

export interface DeadlockPanelProps {
  deadlock: DeadlockState
  scenario: ScenarioState
  /** Los recursos que intervienen en el conflicto (escáner y áreas de carga), para verlos cambiar en vivo. */
  resourcesInPlay: readonly PoolSnapshot[]
  modes: readonly PreventionInfo[]
  onPreventionChange: (mode: PreventionModeId) => void
  /** Lanza el escenario de demostración con la prevención indicada. */
  onStartScenario: (mode: PreventionModeId) => void
  onResolve: (continueOperationId: string) => void
  /** Hay una acción en curso: se deshabilitan los botones. */
  pending: boolean
  error?: string | null
  /** Mensaje de la última resolución (quién continuó y quién volvió a la lista de espera). */
  resolution?: string | null
}

/** Manejo de deadlock: prevención, escenario de demostración y, si ocurre uno, su explicación y resolución. */
export function DeadlockPanel({
  deadlock,
  scenario,
  resourcesInPlay,
  modes,
  onPreventionChange,
  onStartScenario,
  onResolve,
  pending,
  error,
  resolution,
}: DeadlockPanelProps) {
  const scenarioMessage = describeScenario(scenario)

  return (
    <section aria-labelledby="deadlock-title">
      <header className={styles.header}>
        <h2 id="deadlock-title" className={styles.title}>
          Deadlock
        </h2>
        {deadlock.current ? (
          <Badge tone="danger">Deadlock detectado</Badge>
        ) : (
          <Badge tone="success">Sin deadlock</Badge>
        )}
        <span className={styles.counters}>
          {deadlock.detected} detectados · {deadlock.resolved} resueltos
        </span>
      </header>

      <p className={styles.intro}>
        Un deadlock ocurre cuando dos o más operaciones se esperan entre sí para siempre. Aquí puede ocurrir entre un
        pedido en despacho (pide escáner y luego área de carga) y los camiones (piden área de carga y luego escáner,
        el orden inverso).
      </p>

      <DescribedSelect
        id="prevention-select"
        label="Prevención de deadlock"
        options={modes}
        value={deadlock.prevention}
        onChange={onPreventionChange}
      />

      <h3 className={styles.subtitle}>Escenario de demostración</h3>
      <p className={styles.note}>
        Registra un pedido y dos camiones con el desfase justo. Necesita el sistema en reposo: detenga el generador y
        espere a que terminen los pedidos y camiones en curso. A velocidad ×60 todo ocurre en un par de segundos: para
        verlo paso a paso, baje la velocidad a ×1 o ×10 antes de lanzarlo.
      </p>
      <div className={styles.actions}>
        <Button variant="primary" disabled={pending} onClick={() => onStartScenario('NONE')}>
          Provocar deadlock (sin prevención)
        </Button>
        <Button disabled={pending} onClick={() => onStartScenario('GLOBAL_ORDER')}>
          Repetir con orden global
        </Button>
      </div>

      {scenarioMessage && (
        <p className={`${styles.scenario} ${styles[scenarioMessage.tone]}`} role="status">
          {scenarioMessage.text}
        </p>
      )}

      {error && (
        <p className={styles.error} role="alert">
          {error}
        </p>
      )}
      {resolution && (
        <p className={styles.success} role="status">
          {resolution}
        </p>
      )}

      <h3 className={styles.subtitle}>Recursos en juego</h3>
      <p className={styles.note}>
        Cuadrado lleno: quien lo retiene. Borde punteado: quien lo espera. Un deadlock es cuando cada uno espera lo que
        retiene el otro.
      </p>
      <div className={styles.resources}>
        {resourcesInPlay.map((pool) => (
          <ResourceCard
            key={pool.type}
            label={RESOURCE_LABELS[pool.type]}
            capacity={pool.capacity}
            holders={pool.holders}
            waiting={pool.waiting}
            utilization={pool.utilization}
          />
        ))}
      </div>

      <details className={styles.recipe}>
        <summary>¿Cómo provocar un deadlock a mano?</summary>
        <ol>
          <li>Elija «Sin prevención» y detenga el generador.</li>
          <li>
            <strong>Método preciso (velocidad ×1):</strong> registre un pedido de «Caja de medicamentos» (1 unidad) y, unos
            15 segundos después, registre una llegada de 2 camiones. El pedido llega a despacho a los 23 s y toma el
            escáner mientras los camiones, que ya ocuparon las dos áreas de carga, se acomodan; al terminar piden el
            escáner y ya está ocupado.
          </li>
          <li>
            <strong>Método con carga:</strong> ponga el generador en «Saturación» y registre camiones <em>de a uno</em>, con
            unos segundos entre cada uno. No funciona en ráfaga: los camiones hacen fila y, como pasan antes que los pedidos,
            el escáner nunca queda libre para que un pedido lo tome mientras los camiones ya tienen los andenes, y el
            deadlock necesita justo ese hueco.
          </li>
        </ol>
      </details>

      {deadlock.current && (
        <div className={styles.current}>
          <DeadlockAlert report={deadlock.current} onResolve={onResolve} pending={pending} />
        </div>
      )}
    </section>
  )
}
