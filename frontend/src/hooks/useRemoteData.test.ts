import { act, renderHook, waitFor } from '@testing-library/react'
import { useRemoteData } from './useRemoteData'

describe('useRemoteData', () => {
  it('loads the data when mounted', async () => {
    const fetcher = vi.fn().mockResolvedValue(['CUS-001'])

    const { result } = renderHook(() => useRemoteData(fetcher))

    expect(result.current.data).toBeNull()
    await waitFor(() => expect(result.current.data).toEqual(['CUS-001']))
    expect(result.current.error).toBeNull()
  })

  it('exposes the error message when loading fails', async () => {
    const fetcher = vi.fn().mockRejectedValue(new Error('Error 500'))

    const { result } = renderHook(() => useRemoteData(fetcher))

    await waitFor(() => expect(result.current.error).toBe('Error 500'))
    expect(result.current.data).toBeNull()
  })

  it('fetches again on reload and keeps the newest data', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(['a']).mockResolvedValueOnce(['a', 'b'])
    const { result } = renderHook(() => useRemoteData(fetcher))
    await waitFor(() => expect(result.current.data).toEqual(['a']))

    act(() => result.current.reload())

    await waitFor(() => expect(result.current.data).toEqual(['a', 'b']))
    expect(fetcher).toHaveBeenCalledTimes(2)
  })

  it('ignores a response that arrives after unmounting', async () => {
    let resolve: (value: string[]) => void = () => {}
    const fetcher = vi.fn().mockReturnValue(new Promise<string[]>((r) => (resolve = r)))
    const { result, unmount } = renderHook(() => useRemoteData(fetcher))

    unmount()
    resolve(['late'])
    await Promise.resolve()

    expect(result.current.data).toBeNull()
  })
})
