import { render, screen, within } from '@testing-library/react'
import type { LevelStats } from '@/types/simulation'
import { LevelStatsTable } from './LevelStatsTable'

const NAMES = { EXPRESS: 'Exprés', ECONOMY: 'Económico' }

const express: LevelStats = {
  level: 'EXPRESS',
  completed: 12,
  avgWaitMillis: 90_000,
  maxWaitMillis: 300_000,
  avgProcessingMillis: 130_000,
  onTimeRatio: 0.5,
}
const economyEmpty: LevelStats = {
  level: 'ECONOMY',
  completed: 0,
  avgWaitMillis: 0,
  maxWaitMillis: 0,
  avgProcessingMillis: 0,
  onTimeRatio: 0,
}

const rowOf = (name: string) => screen.getByRole('row', { name: new RegExp(`^${name}`) })

describe('LevelStatsTable', () => {
  it('shows the metrics of each level with readable durations', () => {
    render(<LevelStatsTable levels={[express]} serviceLevelNames={NAMES} />)

    const row = within(rowOf('Exprés'))
    expect(row.getByText('12')).toBeInTheDocument()
    expect(row.getByText('1 min 30 s')).toBeInTheDocument()
    expect(row.getByText('5 min 0 s')).toBeInTheDocument()
    expect(row.getByText('2 min 10 s')).toBeInTheDocument()
    expect(row.getByText('50%')).toBeInTheDocument()
  })

  it('shows dashes for a level with no dispatched orders', () => {
    render(<LevelStatsTable levels={[economyEmpty]} serviceLevelNames={NAMES} />)

    expect(within(rowOf('Económico')).getAllByText('—')).toHaveLength(4)
  })

  it.each([
    [0.95, '95%'],
    [0.8, '80%'],
    [0.2, '20%'],
  ])('shows the on-time percentage %s', (ratio, text) => {
    render(<LevelStatsTable levels={[{ ...express, onTimeRatio: ratio }]} serviceLevelNames={NAMES} />)

    expect(screen.getByText(text)).toBeInTheDocument()
  })

  it('falls back to the level id when its name is unknown', () => {
    render(<LevelStatsTable levels={[express]} serviceLevelNames={{}} />)

    expect(screen.getByRole('row', { name: /^EXPRESS/ })).toBeInTheDocument()
  })
})
