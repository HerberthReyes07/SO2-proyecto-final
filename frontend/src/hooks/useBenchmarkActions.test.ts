import { act, renderHook, waitFor } from '@testing-library/react'
import { cancelBenchmark, startBenchmark } from '@/services/benchmarkApi'
import { useBenchmarkActions } from './useBenchmarkActions'

vi.mock('@/services/benchmarkApi')

afterEach(() => vi.resetAllMocks())

describe('useBenchmarkActions', () => {
  it('starts a comparison with the given load', async () => {
    vi.mocked(startBenchmark).mockResolvedValue({} as never)
    const { result } = renderHook(() => useBenchmarkActions())

    act(() => result.current.start({ orders: 50, ordersPerMinute: 3 }))

    await waitFor(() => expect(startBenchmark).toHaveBeenCalledWith({ orders: 50, ordersPerMinute: 3 }))
    expect(result.current.error).toBeNull()
  })

  it('surfaces the server error when a comparison is already running', async () => {
    vi.mocked(startBenchmark).mockRejectedValue(new Error('A comparison is already running'))
    const { result } = renderHook(() => useBenchmarkActions())

    act(() => result.current.start({}))

    await waitFor(() => expect(result.current.error).toBe('A comparison is already running'))
  })

  it('cancels the running comparison', async () => {
    vi.mocked(cancelBenchmark).mockResolvedValue(undefined)
    const { result } = renderHook(() => useBenchmarkActions())

    act(() => result.current.cancel())

    await waitFor(() => expect(cancelBenchmark).toHaveBeenCalledTimes(1))
  })
})
