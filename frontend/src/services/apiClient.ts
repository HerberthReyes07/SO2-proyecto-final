/** Cliente HTTP mínimo para hablar JSON con el backend y traducir sus errores a mensajes legibles. */

type JsonMethod = 'POST' | 'PATCH' | 'PUT'

/** Intenta leer `{ "error": "..." }` del cuerpo; si el servidor no respondió JSON usa el código HTTP. */
async function readErrorMessage(response: Response): Promise<string> {
  try {
    const body: unknown = await response.json()
    if (typeof body === 'object' && body !== null && 'error' in body && typeof body.error === 'string') {
      return body.error
    }
  } catch {
    // El cuerpo no era JSON: se cae al mensaje genérico de abajo.
  }
  return `Error ${response.status}`
}

/** Pide un recurso JSON. Lanza `Error` si el servidor responde con un código de error. */
export async function getJson<T>(url: string): Promise<T> {
  const response = await fetch(url)
  if (!response.ok) {
    throw new Error(await readErrorMessage(response))
  }
  return (await response.json()) as T
}

/** Envía `body` como JSON y devuelve la respuesta ya interpretada. Lanza `Error` si el servidor rechaza la petición. */
export async function sendJson<T>(url: string, method: JsonMethod, body: unknown): Promise<T> {
  const response = await fetch(url, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!response.ok) {
    throw new Error(await readErrorMessage(response))
  }
  return (await response.json()) as T
}
