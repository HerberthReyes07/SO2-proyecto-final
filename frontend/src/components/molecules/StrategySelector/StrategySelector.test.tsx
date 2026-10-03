import { fireEvent, render, screen } from '@testing-library/react'
import type { StrategyInfo } from '@/types/catalog'
import { StrategySelector } from './StrategySelector'

const STRATEGIES: StrategyInfo[] = [
  { id: 'FIRST_FREE', displayName: 'Primera libre', description: 'Llena pasillo por pasillo.' },
  { id: 'LEAST_USED_AISLE', displayName: 'Pasillo menos ocupado', description: 'Reparte entre pasillos.' },
]

describe('StrategySelector', () => {
  it('shows the current strategy and explains it', () => {
    render(<StrategySelector strategies={STRATEGIES} value="FIRST_FREE" onChange={() => {}} />)

    expect(screen.getByLabelText('Estrategia de ubicación')).toHaveDisplayValue('Primera libre')
    expect(screen.getByText('Llena pasillo por pasillo.')).toBeInTheDocument()
  })

  it('reports the chosen strategy', () => {
    const onChange = vi.fn()
    render(<StrategySelector strategies={STRATEGIES} value="FIRST_FREE" onChange={onChange} />)

    fireEvent.change(screen.getByLabelText('Estrategia de ubicación'), { target: { value: 'LEAST_USED_AISLE' } })

    expect(onChange).toHaveBeenCalledWith('LEAST_USED_AISLE')
  })
})
