import { getResourceStatus } from './resourceStatus'

describe('getResourceStatus', () => {
  it('reports an idle pool as available', () => {
    expect(getResourceStatus(0, 6)).toEqual({ label: 'Disponible', tone: 'success' })
  })

  it('reports a partially used pool as in use', () => {
    expect(getResourceStatus(2, 6)).toEqual({ label: 'En uso', tone: 'warning' })
  })

  it('reports a full pool as saturated', () => {
    expect(getResourceStatus(6, 6)).toEqual({ label: 'Saturado', tone: 'danger' })
  })
})
