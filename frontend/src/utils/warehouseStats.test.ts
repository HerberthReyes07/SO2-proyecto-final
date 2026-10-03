import { occupancyPercent, wastePercent } from './warehouseStats'

const layout = { aisles: 2, positions: 2, levels: 2, locationCapacity: 1000 }

describe('occupancyPercent', () => {
  it('is the share of occupied locations', () => {
    expect(occupancyPercent({ usedLocations: 112, totalLocations: 240 })).toBe(47)
    expect(occupancyPercent({ usedLocations: 0, totalLocations: 240 })).toBe(0)
    expect(occupancyPercent({ usedLocations: 240, totalLocations: 240 })).toBe(100)
  })

  it('does not divide by zero', () => {
    expect(occupancyPercent({ usedLocations: 0, totalLocations: 0 })).toBe(0)
  })
})

describe('wastePercent', () => {
  it('is the wasted volume over the capacity of the occupied locations', () => {
    expect(wastePercent({ usedLocations: 4, wastedVolume: 1000, layout })).toBe(25)
  })

  it('is zero when nothing is stored', () => {
    expect(wastePercent({ usedLocations: 0, wastedVolume: 0, layout })).toBe(0)
  })
})
