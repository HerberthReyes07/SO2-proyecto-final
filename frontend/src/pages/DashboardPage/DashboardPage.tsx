import { useState } from 'react'
import { Badge } from '@/components/atoms/Badge'
import { ActiveOrdersPanel } from '@/components/organisms/ActiveOrdersPanel'
import { AlertBanner } from '@/components/molecules/AlertBanner'
import { BenchmarkPanel } from '@/components/organisms/BenchmarkPanel'
import { ClockPanel } from '@/components/organisms/ClockPanel'
import { CustomerForm } from '@/components/organisms/CustomerForm'
import { DeadlockPanel } from '@/components/organisms/DeadlockPanel'
import { GeneratorPanel } from '@/components/organisms/GeneratorPanel'
import { InventoryPanel } from '@/components/organisms/InventoryPanel'
import { OrderForm } from '@/components/organisms/OrderForm'
import { ReceptionForm } from '@/components/organisms/ReceptionForm'
import { ReceptionPanel } from '@/components/organisms/ReceptionPanel'
import { ResourcePanel } from '@/components/organisms/ResourcePanel'
import { StatsPanel } from '@/components/organisms/StatsPanel'
import { TabbedContent } from '@/components/organisms/TabbedContent'
import { WarehousePanel } from '@/components/organisms/WarehousePanel'
import { WaitingListPanel } from '@/components/organisms/WaitingListPanel'
import { DashboardTemplate } from '@/components/templates/DashboardTemplate'
import { useBenchmarkActions } from '@/hooks/useBenchmarkActions'
import { useClockControls } from '@/hooks/useClockControls'
import { useDeadlockActions } from '@/hooks/useDeadlockActions'
import { useCatalog, useCustomers } from '@/hooks/useReferenceData'
import { useReferenceOptions } from '@/hooks/useReferenceOptions'
import { useRemoteAction } from '@/hooks/useRemoteAction'
import { useSimulationStream } from '@/hooks/useSimulationStream'
import { createCustomer } from '@/services/customerApi'
import { updateGenerator } from '@/services/generatorApi'
import { placeOrder } from '@/services/orderApi'
import { registerArrival } from '@/services/receptionApi'
import { changePolicy } from '@/services/schedulingApi'
import { resetStats } from '@/services/statsApi'
import { changeStrategy } from '@/services/warehouseApi'
import { describeArrival } from '@/utils/describeArrival'
import type {
  CreateCustomerRequest,
  PlaceOrderRequest,
  PlacementStrategyId,
  PolicyId,
  RegisterArrivalRequest,
} from '@/types/catalog'
import type { ConnectionStatus } from '@/types/simulation'

const CONNECTION_BADGES: Record<ConnectionStatus, { label: string; tone: 'success' | 'warning' | 'danger' }> = {
  open: { label: 'Conectado', tone: 'success' },
  connecting: { label: 'Conectando…', tone: 'warning' },
  closed: { label: 'Desconectado', tone: 'danger' },
}

const TABS = [
  { id: 'overview', label: 'Resumen' },
  { id: 'orders', label: 'Pedidos' },
  { id: 'inventory', label: 'Inventario' },
  { id: 'warehouse', label: 'Almacén' },
  { id: 'deadlock', label: 'Deadlock' },
  { id: 'benchmark', label: 'Comparador' },
] as const

type TabId = (typeof TABS)[number]['id']

/** Página principal: conecta los datos en vivo y las acciones del usuario con los componentes de presentación. */
export function DashboardPage() {
  const { snapshot, status } = useSimulationStream()
  const clockControls = useClockControls()
  const catalog = useCatalog()
  const customers = useCustomers()
  const options = useReferenceOptions(catalog.data, customers.data)
  const [activeTab, setActiveTab] = useState<TabId>('overview')

  const customerAction = useRemoteAction(createCustomer)
  const orderAction = useRemoteAction(placeOrder)
  const arrivalAction = useRemoteAction(registerArrival)
  const policyAction = useRemoteAction(changePolicy)
  const generatorAction = useRemoteAction(updateGenerator)
  const resetAction = useRemoteAction(resetStats)
  const strategyAction = useRemoteAction(changeStrategy)
  const deadlockActions = useDeadlockActions()
  const benchmarkActions = useBenchmarkActions()

  const handleCreateCustomer = async (request: CreateCustomerRequest) => {
    const created = await customerAction.run(request)
    if (created) customers.reload() // para que el nuevo cliente aparezca en el formulario de pedidos
    return created !== null
  }
  const handlePlaceOrder = async (request: PlaceOrderRequest) => (await orderAction.run(request)) !== null
  const handleRegisterArrival = async (request: RegisterArrivalRequest) => (await arrivalAction.run(request)) !== null
  const handlePolicyChange = (policy: PolicyId) => void policyAction.run(policy)
  const handleStrategyChange = (strategy: PlacementStrategyId) => void strategyAction.run(strategy)
  const handleIntensityChange = (ordersPerMinute: number) => void generatorAction.run({ ordersPerMinute })
  const handleGeneratorEnabled = (enabled: boolean, seed?: number) => void generatorAction.run({ enabled, seed })

  const connection = CONNECTION_BADGES[status]
  const referenceDataError = catalog.error ?? customers.error
  const arrivalConfirmation = arrivalAction.result && describeArrival(arrivalAction.result.truckIds)

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
              <ReceptionForm
                productOptions={options.productOptions}
                onSubmit={handleRegisterArrival}
                pending={arrivalAction.pending}
                error={arrivalAction.error}
                confirmation={arrivalConfirmation}
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
      {snapshot?.deadlock.current && (
        <AlertBanner
          message={`Deadlock detectado (${snapshot.deadlock.current.id}): ${snapshot.deadlock.current.operations.length} operaciones bloqueadas.`}
          actionLabel="Ver y resolver"
          onAction={() => setActiveTab('deadlock')}
        />
      )}
      {snapshot ? (
        <TabbedContent
          tabs={TABS}
          activeId={activeTab}
          onChange={(id) => setActiveTab(id as TabId)}
          idPrefix="dashboard"
        >
          {activeTab === 'overview' && (
            <>
              <StatsPanel
                stats={snapshot.stats}
                serviceLevelNames={options.serviceLevelNames}
                onReset={() => void resetAction.run()}
                resetting={resetAction.pending}
                error={resetAction.error}
              />
              <ResourcePanel resources={snapshot.resources} />
            </>
          )}
          {activeTab === 'orders' && (
            <>
              <WaitingListPanel
                policies={catalog.data?.policies ?? []}
                currentPolicy={snapshot.scheduling.policy}
                waiting={snapshot.scheduling.waiting}
                waitingTotal={snapshot.scheduling.waitingTotal}
                serviceLevelNames={options.serviceLevelNames}
                productNames={options.productNames}
                onPolicyChange={handlePolicyChange}
                policyError={policyAction.error}
              />
              <ActiveOrdersPanel
                orders={snapshot.activeOrders}
                completedCount={snapshot.completedOrders}
                serviceLevelNames={options.serviceLevelNames}
              />
            </>
          )}
          {activeTab === 'inventory' && (
            <>
              <ReceptionPanel
                reception={snapshot.reception}
                inventory={snapshot.inventory}
                productNames={options.productNames}
              />
              <InventoryPanel stock={snapshot.inventory.stock} productNames={options.productNames} />
            </>
          )}
          {activeTab === 'deadlock' && (
            <DeadlockPanel
              deadlock={snapshot.deadlock}
              scenario={snapshot.scenario}
              resourcesInPlay={snapshot.resources.filter((pool) => pool.type === 'SCANNER' || pool.type === 'LOADING_DOCK')}
              modes={catalog.data?.preventionModes ?? []}
              onPreventionChange={deadlockActions.changePrevention}
              onStartScenario={deadlockActions.startScenario}
              onResolve={deadlockActions.resolve}
              pending={deadlockActions.pending}
              error={deadlockActions.error}
              resolution={deadlockActions.resolution}
            />
          )}
          {activeTab === 'benchmark' && (
            <BenchmarkPanel
              benchmark={snapshot.benchmark}
              policies={catalog.data?.policies ?? []}
              currentPolicy={snapshot.scheduling.policy}
              serviceLevelNames={options.serviceLevelNames}
              onStart={benchmarkActions.start}
              onCancel={benchmarkActions.cancel}
              onUsePolicy={handlePolicyChange}
              pending={benchmarkActions.pending}
              error={benchmarkActions.error ?? policyAction.error}
            />
          )}
          {activeTab === 'warehouse' && (
            <WarehousePanel
              warehouse={snapshot.warehouse}
              strategies={catalog.data?.placementStrategies ?? []}
              productNames={options.productNames}
              productTypeIds={options.productTypeIds}
              merchandiseTypeNames={options.merchandiseTypeNames}
              onStrategyChange={handleStrategyChange}
              strategyError={strategyAction.error}
            />
          )}
        </TabbedContent>
      ) : (
        <p>Esperando datos del servidor…</p>
      )}
    </DashboardTemplate>
  )
}
