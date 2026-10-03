import { fireEvent, render, screen } from '@testing-library/react'
import type { PreventionInfo } from '@/types/catalog'
import type { DeadlockState, PoolSnapshot, ScenarioState } from '@/types/simulation'
import { DeadlockPanel } from './DeadlockPanel'

const MODES: PreventionInfo[] = [
  { id: 'NONE', displayName: 'Sin prevención', description: 'Puede producirse un deadlock.' },
  { id: 'GLOBAL_ORDER', displayName: 'Orden global de recursos', description: 'Rompe la espera circular.' },
]

const HEALTHY: DeadlockState = { prevention: 'GLOBAL_ORDER', current: null, detected: 0, resolved: 0 }

const DEADLOCKED: DeadlockState = {
  prevention: 'NONE',
  detected: 1,
  resolved: 0,
  current: {
    id: 'DL-0001',
    detectedAtMillis: 0,
    operations: [
      { id: 'ORD-0001', kind: 'ORDER', held: ['SCANNER'], waitingFor: 'LOADING_DOCK' },
      { id: 'TRK-0001', kind: 'TRUCK', held: ['LOADING_DOCK'], waitingFor: 'SCANNER' },
    ],
    resources: ['SCANNER', 'LOADING_DOCK'],
    conflict: { waiter: 'ORD-0001', holder: 'TRK-0001' },
  },
}

const NO_SCENARIO: ScenarioState = { phase: 'IDLE', mode: null }

const SCANNER: PoolSnapshot = { type: 'SCANNER', capacity: 1, holders: ['ORD-0001'], waiting: ['TRK-0001', 'TRK-0002'], utilization: 0.3 }
const DOCKS: PoolSnapshot = { type: 'LOADING_DOCK', capacity: 2, holders: ['TRK-0001', 'TRK-0002'], waiting: ['ORD-0001'], utilization: 0.5 }

function setup(deadlock: DeadlockState, extra: Partial<React.ComponentProps<typeof DeadlockPanel>> = {}) {
  const props = {
    deadlock,
    scenario: NO_SCENARIO,
    resourcesInPlay: [SCANNER, DOCKS],
    modes: MODES,
    onPreventionChange: vi.fn(),
    onStartScenario: vi.fn(),
    onResolve: vi.fn(),
    pending: false,
    ...extra,
  }
  render(<DeadlockPanel {...props} />)
  return props
}

describe('DeadlockPanel', () => {
  it('reports a healthy system with its counters and the current prevention', () => {
    setup({ ...HEALTHY, detected: 3, resolved: 2 })

    expect(screen.getByText('Sin deadlock')).toBeInTheDocument()
    expect(screen.getByText('3 detectados · 2 resueltos')).toBeInTheDocument()
    expect(screen.getByLabelText('Prevención de deadlock')).toHaveDisplayValue('Orden global de recursos')
    expect(screen.queryByRole('heading', { name: /Deadlock detectado \(/ })).not.toBeInTheDocument()
  })

  it('changes the prevention mode', () => {
    const { onPreventionChange } = setup(HEALTHY)

    fireEvent.change(screen.getByLabelText('Prevención de deadlock'), { target: { value: 'NONE' } })

    expect(onPreventionChange).toHaveBeenCalledWith('NONE')
  })

  it('launches the demonstration scenario both without and with prevention', () => {
    const { onStartScenario } = setup(HEALTHY)

    fireEvent.click(screen.getByRole('button', { name: 'Provocar deadlock (sin prevención)' }))
    fireEvent.click(screen.getByRole('button', { name: 'Repetir con orden global' }))

    expect(onStartScenario).toHaveBeenNthCalledWith(1, 'NONE')
    expect(onStartScenario).toHaveBeenNthCalledWith(2, 'GLOBAL_ORDER')
  })

  it('shows the alert with the resolution choices when a deadlock is confirmed', () => {
    const { onResolve } = setup(DEADLOCKED)

    expect(screen.getByText('Deadlock detectado')).toBeInTheDocument() // la insignia
    expect(screen.getByRole('heading', { name: 'Deadlock detectado (DL-0001)' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Que continúe TRK-0001' }))

    expect(onResolve).toHaveBeenCalledWith('TRK-0001')
  })

  it('shows errors and the result of the last resolution', () => {
    setup(HEALTHY, { error: 'The scenario needs an idle system', resolution: 'ORD-0001 continuó; TRK-0001 volvió a la lista de espera.' })

    expect(screen.getByRole('alert')).toHaveTextContent('The scenario needs an idle system')
    expect(screen.getByRole('status')).toHaveTextContent('TRK-0001 volvió a la lista de espera.')
  })

  it('shows the scanner and the loading docks live, with who holds and who waits for each', () => {
    setup(HEALTHY)

    expect(screen.getByRole('list', { name: 'Operaciones usando Sistema de escaneo' })).toHaveTextContent('ORD-0001')
    expect(screen.getByRole('list', { name: 'Operaciones esperando Sistema de escaneo' })).toHaveTextContent('TRK-0001')
    expect(screen.getByRole('list', { name: 'Operaciones usando Áreas de carga' })).toHaveTextContent('TRK-0002')
    expect(screen.getByRole('list', { name: 'Operaciones esperando Áreas de carga' })).toHaveTextContent('ORD-0001')
  })

  it('tells how the demonstration scenario is going and how it ended', () => {
    const { rerender } = render(
      <DeadlockPanel deadlock={HEALTHY} scenario={{ phase: 'RUNNING', mode: 'GLOBAL_ORDER' }} resourcesInPlay={[]} modes={MODES}
        onPreventionChange={() => {}} onStartScenario={() => {}} onResolve={() => {}} pending={false} />,
    )
    expect(screen.getByRole('status')).toHaveTextContent('Escenario en curso (con orden global)')

    rerender(
      <DeadlockPanel deadlock={HEALTHY} scenario={{ phase: 'FINISHED_CLEAN', mode: 'GLOBAL_ORDER' }} resourcesInPlay={[]} modes={MODES}
        onPreventionChange={() => {}} onStartScenario={() => {}} onResolve={() => {}} pending={false} />,
    )
    expect(screen.getByRole('status')).toHaveTextContent('no hubo deadlock')
  })

  it('shows no scenario message before any scenario has run', () => {
    setup(HEALTHY)

    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('explains how to provoke a deadlock by hand, including why a burst of trucks does not work', () => {
    setup(HEALTHY)

    expect(screen.getByText('¿Cómo provocar un deadlock a mano?')).toBeInTheDocument()
    expect(screen.getByText(/de a uno/)).toBeInTheDocument()
    expect(screen.getByText(/No funciona en ráfaga/)).toBeInTheDocument()
  })

  it('disables the actions while one is in progress', () => {
    setup(HEALTHY, { pending: true })

    expect(screen.getByRole('button', { name: 'Provocar deadlock (sin prevención)' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Repetir con orden global' })).toBeDisabled()
  })
})
