import { sendJson } from './apiClient'

function stubFetch(response: Partial<Response> & { json: () => Promise<unknown> }) {
  const fetchMock = vi.fn().mockResolvedValue(response)
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

afterEach(() => vi.unstubAllGlobals())

describe('sendJson', () => {
  it('sends the body as JSON and returns the parsed response', async () => {
    const fetchMock = stubFetch({ ok: true, json: async () => ({ speed: 60 }) })

    const result = await sendJson('/api/clock', 'PATCH', { speed: 60 })

    expect(result).toEqual({ speed: 60 })
    expect(fetchMock).toHaveBeenCalledWith('/api/clock', {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: '{"speed":60}',
    })
  })

  it('throws the message sent by the backend when the request is rejected', async () => {
    stubFetch({ ok: false, status: 400, json: async () => ({ error: 'speed must be between 0.1 and 100000.0' }) })

    await expect(sendJson('/api/clock', 'PATCH', { speed: 0 })).rejects.toThrow('speed must be between')
  })

  it('falls back to the HTTP status when the error body is not JSON', async () => {
    stubFetch({
      ok: false,
      status: 502,
      json: async () => {
        throw new SyntaxError('Unexpected token <')
      },
    })

    await expect(sendJson('/api/clock', 'PATCH', {})).rejects.toThrow('Error 502')
  })
})
