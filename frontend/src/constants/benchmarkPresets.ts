import type { IntensityPreset } from '@/constants/intensityPresets'

/**
 * Intensidades de llegada para la comparación, en pedidos por minuto simulado. El sistema despacha unos 2
 * pedidos/min como máximo, así que con "Moderada" hay una cola pequeña y con "Saturación" crece mucho; es en
 * saturación donde las políticas se separan más, porque hay a quién preferir.
 */
export const BENCHMARK_INTENSITY_PRESETS: readonly IntensityPreset[] = [
  { label: 'Moderada', ordersPerMinute: 1.5 },
  { label: 'Alta', ordersPerMinute: 3 },
  { label: 'Saturación', ordersPerMinute: 6 },
]

export const DEFAULT_BENCHMARK_ORDERS = 120
export const DEFAULT_BENCHMARK_ORDERS_PER_MINUTE = 3
/** Mismos límites que valida el backend en `BenchmarkParams`. */
export const MIN_BENCHMARK_ORDERS = 10
export const MAX_BENCHMARK_ORDERS = 500
