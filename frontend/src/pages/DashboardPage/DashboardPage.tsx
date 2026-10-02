import { Badge } from '@/components/atoms/Badge'
import { ActiveOrdersPanel } from '@/components/organisms/ActiveOrdersPanel'
import { ClockPanel } from '@/components/organisms/ClockPanel'
import { CustomerForm } from '@/components/organisms/CustomerForm'
import { GeneratorPanel } from '@/components/organisms/GeneratorPanel'
import { OrderForm } from '@/components/organisms/OrderForm'
import { ResourcePanel } from '@/components/organisms/ResourcePanel'
import { StatsPanel } from '@/components/organisms/StatsPanel'
import { WaitingListPanel } from '@/components/organisms/WaitingListPanel'
import { DashboardTemplate } from '@/components/templates/DashboardTemplate'
import { useClockControls } from '@/hooks/useClockControls'
import { useCatalog, useCustomers } from '@/hooks/useReferenceData'
import { useReferenceOptions } from '@/hooks/useReferenceOptions'
import { useRemoteAction } from '@/hooks/useRemoteAction'
import { useSimulationStream } from '@/hooks/useSimulationStream'
import { createCustomer } from '@/services/customerApi'
import { updateGenerator } from '@/services/generatorApi'
import { placeOrder } from '@/services/orderApi'
import { changePolicy } from '@/services/schedulingApi'
import { resetStats } from '@/services/statsApi'
import type { CreateCustomerRequest, PlaceOrderRequest, PolicyId } from '@/types/catalog'
import type { ConnectionStatus } from '@/types/simulation'

const CONNECTION_BADGES: Record<ConnectionStatus, { label: string; tone: 'success' | 'warning' | 'danger' }> = {
  open: { label: 'Conectado', tone: 'success' },
  connecting: { label: 'Conectando…', tone: 'warning' },
  closed: { label: 'Desconectado', tone: 'danger' },
}

/** Página principal: conecta los datos en vivo y las acciones del usuario con los componentes de presentación. */
export function DashboardPage() {
  const { snapshot, status } = useSimulationStream()
  const clockControls = useClockControls()
  const catalog = useCatalog()
  const customers = useCustomers()
  const options = useReferenceOptions(catalog.data, customers.data)

  const customerAction = useRemoteAction(createCustomer)
  const orderAction = useRemoteAction(placeOrder)
  const policyAction = useRemoteAction(changePolicy)
  const generatorAction = useRemoteAction(updateGenerator)
  const resetAction = useRemoteAction(resetStats)

  const handleCreateCustomer = async (request: CreateCustomerRequest) => {
    const created = await customerAction.run(request)
    if (created) customers.reload() // para que el nuevo cliente aparezca en el formulario de pedidos
    return created !== null
  }
  const handlePlaceOrder = async (request: PlaceOrderRequest) => (await orderAction.run(request)) !== null
  const handlePolicyChange = (policy: PolicyId) => void policyAction.run(policy)
  const handleIntensityChange = (ordersPerMinute: number) => void generatorAction.run({ ordersPerMinute })
  const handleGeneratorEnabled = (enabled: boolean, seed?: number) => void generatorAction.run({ enabled, seed })

  const connection = CONNECTION_BADGES[status]
  const referenceDataError = catalog.error ?? customers.error

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
      sidebar={
        <>
          {snapshot && (
            <GeneratorPanel
              generator={snapshot.generator}
              onIntensityChange={handleIntensityChange}
              onEnabledChange={handleGeneratorEnabled}
              pending={generatorAction.pending}
              error={generatorAction.error}
            />
          )}
          {referenceDataError ? (
            <p role="alert">No se pudieron cargar los datos del formulario: {referenceDataError}</p>
          ) : (
            <>
              <OrderForm
                customerOptions={options.customerOptions}
                productOptions={options.productOptions}
                onSubmit={handlePlaceOrder}
                pending={orderAction.pending}
                error={orderAction.error}
                confirmation={orderAction.result && `Pedido ${orderAction.result.orderId} registrado`}
              />
              <CustomerForm
                serviceLevelOptions={options.serviceLevelOptions}
                onSubmit={handleCreateCustomer}
                pending={customerAction.pending}
                error={customerAction.error}
                confirmation={customerAction.result && `Cuenta ${customerAction.result.id} creada`}
              />
            </>
          )}
        </>
      }
    >
      {snapshot ? (
        <>
          <StatsPanel
            stats={snapshot.stats}
            serviceLevelNames={options.serviceLevelNames}
            onReset={() => void resetAction.run()}
            resetting={resetAction.pending}
            error={resetAction.error}
          />
          <WaitingListPanel
            policies={catalog.data?.policies ?? []}
            currentPolicy={snapshot.scheduling.policy}
            waiting={snapshot.scheduling.waiting}
            waitingTotal={snapshot.scheduling.waitingTotal}
            serviceLevelNames={options.serviceLevelNames}
            onPolicyChange={handlePolicyChange}
            policyError={policyAction.error}
          />
          <ActiveOrdersPanel
            orders={snapshot.activeOrders}
            completedCount={snapshot.completedOrders}
            serviceLevelNames={options.serviceLevelNames}
          />
          <ResourcePanel resources={snapshot.resources} />
        </>
      ) : (
        <p>Esperando datos del servidor…</p>
      )}
    </DashboardTemplate>
  )
}
