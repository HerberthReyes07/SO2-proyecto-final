/** Construye la URL del WebSocket respetando http/https del sitio que sirve la aplicación. */
export function buildSocketUrl(location: Pick<Location, 'protocol' | 'host'> = window.location): string {
  const scheme = location.protocol === 'https:' ? 'wss' : 'ws'
  return `${scheme}://${location.host}/ws`
}
