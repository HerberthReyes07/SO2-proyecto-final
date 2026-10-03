/** Resume los camiones registrados: uno por uno si son pocos, "30 camiones registrados (TRK-0001 a TRK-0030)" si son muchos. */
export function describeArrival(truckIds: readonly string[]): string {
  if (truckIds.length === 0) return 'No se registró ningún camión'
  if (truckIds.length === 1) return `Camión ${truckIds[0]} registrado`
  return `${truckIds.length} camiones registrados (${truckIds[0]} a ${truckIds[truckIds.length - 1]})`
}
