import { act, renderHook } from '@testing-library/react'
import { useRemoteAction } from './useRemoteAction'

describe('useRemoteAction', () => {
  it('returns the result and clears the error on success', async () => {
    const action = vi.fn().mockResolvedValue({ orderId: 'ORD-0001' })
    const { result } = renderHook(() => useRemoteAction(action))

    let outcome: unknown
    await act(async () => {
      outcome = await result.current.run('a', 1)
    })

    expect(outcome).toEqual({ orderId: 'ORD-0001' })
    expect(action).toHaveBeenCalledWith('a', 1)
    expect(result.current.result).toEqual({ orderId: 'ORD-0001' })
    expect(result.current.error).toBeNull()
    expect(result.current.pending).toBe(false)
  })

  it('returns null and exposes the message when the action fails', async () => {
    const action = vi.fn().mockRejectedValue(new Error('Unknown customer: CUS-999'))
    const { result } = renderHook(() => useRemoteAction(action))

    let outcome: unknown
    await act(async () => {
      outcome = await result.current.run()
    })

    expect(outcome).toBeNull()
    expect(result.current.error).toBe('Unknown customer: CUS-999')
    expect(result.current.result).toBeNull()
  })

  it('is pending while the action is in flight', async () => {
    let finish: (value: string) => void = () => {}
    const action = vi.fn().mockReturnValue(new Promise<string>((resolve) => (finish = resolve)))
    const { result } = renderHook(() => useRemoteAction(action))

    let running: Promise<unknown> = Promise.resolve()
    act(() => {
      running = result.current.run()
    })
    expect(result.current.pending).toBe(true)

    await act(async () => {
      finish('done')
      await running
    })
    expect(result.current.pending).toBe(false)
  })
})
