import { useMemo } from 'react'
import { Badge } from '@/components/atoms/Badge'
import { ActiveOrdersPanel } from '@/components/organisms/ActiveOrdersPanel'
import { ClockPanel } from '@/components/organisms/ClockPanel'
import { CustomerForm } from '@/components/organisms/CustomerForm'
import { OrderForm } from '@/components/organisms/OrderForm'
import { ResourcePanel } from '@/components/organisms/ResourcePanel'
import { WaitingListPanel } from '@/components/organisms/WaitingListPanel'
import { DashboardTemplate } from '@/components/templates/DashboardTemplate'
import { useClockControls } from '@/hooks/useClockControls'
import { useCatalog, useCustomers } from '@/hooks/useReferenceData'
import { useRemoteAction } from '@/hooks/useRemoteAction'
import { useSimulationStream } from '@/hooks/useSimulationStream'
import { createCustomer } from '@/services/customerApi'
import { placeOrder } from '@/services/orderApi'
import { changePolicy } from '@/services/schedulingApi'
import type { CreateCustomerRequest, PlaceOrderRequest, PolicyId } from '@/types/catalog'
import type { ConnectionStatus } from '@/types/simulation'
import {
  buildCustomerOptions,
  buildProductOptions,
  buildServiceLevelOptions,
  serviceLevelNames,
} from '@/utils/options'

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
  const customerAction = useRemoteAction(createCustomer)
  const orderAction = useRemoteAction(placeOrder)
  const policyAction = useRemoteAction(changePolicy)

  const serviceLevels = catalog.data?.serviceLevels
  const levelOptions = useMemo(() => buildServiceLevelOptions(serviceLevels ?? []), [serviceLevels])
  const levelNames = useMemo(() => serviceLevelNames(serviceLevels ?? []), [serviceLevels])
  const productOptions = useMemo(() => (catalog.data ? buildProductOptions(catalog.data) : []), [catalog.data])
  const customerOptions = useMemo(
    () => buildCustomerOptions(customers.data ?? [], serviceLevels ?? []),
    [customers.data, serviceLevels],
  )

  const handleCreateCustomer = async (request: CreateCustomerRequest) => {
    const created = await customerAction.run(request)
    if (created) customers.reload() // para que el nuevo cliente aparezca en el formulario de pedidos
    return created !== null
  }
  const handlePlaceOrder = async (request: PlaceOrderRequest) => (await orderAction.run(request)) !== null
  const handlePolicyChange = (policy: PolicyId) => void policyAction.run(policy)

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
        referenceDataError ? (
          <p role="alert">No se pudieron cargar los datos del formulario: {referenceDataError}</p>
        ) : (
          <>
            <OrderForm
              customerOptions={customerOptions}
              productOptions={productOptions}
              onSubmit={handlePlaceOrder}
              pending={orderAction.pending}
              error={orderAction.error}
              confirmation={orderAction.result && `Pedido ${orderAction.result.orderId} registrado`}
            />
            <CustomerForm
              serviceLevelOptions={levelOptions}
              onSubmit={handleCreateCustomer}
              pending={customerAction.pending}
              error={customerAction.error}
              confirmation={customerAction.result && `Cuenta ${customerAction.result.id} creada`}
            />
          </>
        )
      }
    >
      {snapshot ? (
        <>
          <WaitingListPanel
            policies={catalog.data?.policies ?? []}
            currentPolicy={snapshot.scheduling.policy}
            waiting={snapshot.scheduling.waiting}
            waitingTotal={snapshot.scheduling.waitingTotal}
            serviceLevelNames={levelNames}
            onPolicyChange={handlePolicyChange}
            policyError={policyAction.error}
          />
          <ActiveOrdersPanel
            orders={snapshot.activeOrders}
            completedCount={snapshot.completedOrders}
            serviceLevelNames={levelNames}
          />
          <ResourcePanel resources={snapshot.resources} />
        </>
      ) : (
        <p>Esperando datos del servidor…</p>
      )}
    </DashboardTemplate>
  )
}
