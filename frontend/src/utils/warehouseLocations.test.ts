import type { WarehouseLayout } from '@/types/simulation'
import { locationCoordinates, locationLabel } from './warehouseLocations'

const LAYOUT: WarehouseLayout = { aisles: 6, positions: 8, levels: 5, locationCapacity: 1000 }

describe('locationCoordinates', () => {
  it('numbers locations aisle first, then position, with the level changing fastest', () => {
    expect(locationCoordinates(LAYOUT, 0)).toEqual({ aisle: 0, position: 0, level: 0 })
    expect(locationCoordinates(LAYOUT, 1)).toEqual({ aisle: 0, position: 0, level: 1 })
    expect(locationCoordinates(LAYOUT, 5)).toEqual({ aisle: 0, position: 1, level: 0 })
    expect(locationCoordinates(LAYOUT, 40)).toEqual({ aisle: 1, position: 0, level: 0 })
  })

  it('covers every location exactly once', () => {
    const seen = new Set<string>()
    for (let index = 0; index < 240; index++) {
      const { aisle, position, level } = locationCoordinates(LAYOUT, index)
      seen.add(`${aisle}-${position}-${level}`)
    }
    expect(seen.size).toBe(240)
  })
})

describe('locationLabel', () => {
  it('is one-based and readable, and matches the backend labels', () => {
    expect(locationLabel(LAYOUT, 0)).toBe('A1-P1-N1')
    expect(locationLabel(LAYOUT, 40 + 2 * 5 + 4)).toBe('A2-P3-N5')
    expect(locationLabel(LAYOUT, 239)).toBe('A6-P8-N5')
  })
})
