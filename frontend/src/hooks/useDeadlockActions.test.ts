import { act, renderHook, waitFor } from '@testing-library/react'
import { changePrevention, resolveDeadlock, startScenario } from '@/services/deadlockApi'
import { useDeadlockActions } from './useDeadlockActions'

vi.mock('@/services/deadlockApi')

afterEach(() => vi.resetAllMocks())

describe('useDeadlockActions', () => {
  it('resolves a deadlock and reports who continued and who went back to waiting', async () => {
    vi.mocked(resolveDeadlock).mockResolvedValue({ continued: 'ORD-0001', returnedToWaiting: 'TRK-0001' })
    const { result } = renderHook(() => useDeadlockActions())

    act(() => result.current.resolve('ORD-0001'))

    await waitFor(() => expect(result.current.resolution).toBe('ORD-0001 continuó; TRK-0001 volvió a la lista de espera.'))
    expect(resolveDeadlock).toHaveBeenCalledWith('ORD-0001')
    expect(result.current.error).toBeNull()
  })

  it('surfaces the server error when the deadlock is gone', async () => {
    vi.mocked(resolveDeadlock).mockRejectedValue(new Error('There is no deadlock to resolve'))
    const { result } = renderHook(() => useDeadlockActions())

    act(() => result.current.resolve('ORD-0001'))

    await waitFor(() => expect(result.current.error).toBe('There is no deadlock to resolve'))
    expect(result.current.resolution).toBeNull()
  })

  it('starts the scenario and changes the prevention with the chosen mode', async () => {
    vi.mocked(startScenario).mockResolvedValue({ prevention: 'NONE' })
    vi.mocked(changePrevention).mockResolvedValue({ prevention: 'GLOBAL_ORDER' })
    const { result } = renderHook(() => useDeadlockActions())

    act(() => result.current.startScenario('NONE'))
    act(() => result.current.changePrevention('GLOBAL_ORDER'))

    await waitFor(() => expect(startScenario).toHaveBeenCalledWith('NONE'))
    await waitFor(() => expect(changePrevention).toHaveBeenCalledWith('GLOBAL_ORDER'))
  })

  it('is pending while an action is in flight', async () => {
    let finish: (value: { prevention: 'NONE' }) => void = () => {}
    vi.mocked(startScenario).mockReturnValue(new Promise((resolve) => (finish = resolve)))
    const { result } = renderHook(() => useDeadlockActions())

    act(() => result.current.startScenario('NONE'))
    expect(result.current.pending).toBe(true)

    await act(async () => finish({ prevention: 'NONE' }))
    expect(result.current.pending).toBe(false)
  })
})
