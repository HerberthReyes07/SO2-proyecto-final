/**
 * Intensidades ofrecidas al usuario, en pedidos por minuto simulado. Medido con las duraciones por
 * defecto y la mezcla de pedidos por defecto, el sistema despacha unos 2 pedidos/min como máximo
 * (lo limitan los 10 encargados, que pasan casi todo el tiempo ocupados): "Alta" queda cerca de ese
 * límite y "Saturación" lo supera, de modo que la lista de espera crece sin parar.
 */
export interface IntensityPreset {
  label: string
  ordersPerMinute: number
}

export const INTENSITY_PRESETS: readonly IntensityPreset[] = [
  { label: 'Baja', ordersPerMinute: 0.5 },
  { label: 'Normal', ordersPerMinute: 1 },
  { label: 'Alta', ordersPerMinute: 1.5 },
  { label: 'Saturación', ordersPerMinute: 3 },
]
