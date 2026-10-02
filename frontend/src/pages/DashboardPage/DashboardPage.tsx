import { Badge } from '@/components/atoms/Badge'
import { ClockPanel } from '@/components/organisms/ClockPanel'
import { ResourcePanel } from '@/components/organisms/ResourcePanel'
import { DashboardTemplate } from '@/components/templates/DashboardTemplate'
import { useClockControls } from '@/hooks/useClockControls'
import { useSimulationStream } from '@/hooks/useSimulationStream'
import type { ConnectionStatus } from '@/types/simulation'

const CONNECTION_BADGES: Record<ConnectionStatus, { label: string; tone: 'success' | 'warning' | 'danger' }> = {
  open: { label: 'Conectado', tone: 'success' },
  connecting: { label: 'Conectando…', tone: 'warning' },
  closed: { label: 'Desconectado', tone: 'danger' },
}

/** Página principal: conecta los datos en vivo con los componentes de presentación. */
export function DashboardPage() {
  const { snapshot, status } = useSimulationStream()
  const clockControls = useClockControls()
  const connection = CONNECTION_BADGES[status]

  return (
    <DashboardTemplate
      title="REDXela · Centro de distribución"
      status={
        <>
          {snapshot && (
            <ClockPanel
              clock={snapshot.clock}
              onSpeedChange={clockControls.setSpeed}
              onPausedChange={clockControls.setPaused}
              error={clockControls.error}
            />
          )}
          <Badge tone={connection.tone}>{connection.label}</Badge>
        </>
      }
    >
      {snapshot ? <ResourcePanel resources={snapshot.resources} /> : <p>Esperando datos del servidor…</p>}
    </DashboardTemplate>
  )
}
