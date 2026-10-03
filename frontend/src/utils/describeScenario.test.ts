import { describeScenario } from './describeScenario'

describe('describeScenario', () => {
  it('says nothing when no scenario has run', () => {
    expect(describeScenario({ phase: 'IDLE', mode: null })).toBeNull()
  })

  it('says it is running and with which prevention', () => {
    const message = describeScenario({ phase: 'RUNNING', mode: 'GLOBAL_ORDER' })

    expect(message?.tone).toBe('info')
    expect(message?.text).toContain('en curso (con orden global)')
  })

  it('says that with prevention it ended without a deadlock', () => {
    const message = describeScenario({ phase: 'FINISHED_CLEAN', mode: 'GLOBAL_ORDER' })

    expect(message?.tone).toBe('success')
    expect(message?.text).toContain('no hubo deadlock')
  })

  it('says that without prevention it ended after a deadlock that was resolved', () => {
    const message = describeScenario({ phase: 'FINISHED_WITH_DEADLOCK', mode: 'NONE' })

    expect(message?.tone).toBe('warning')
    expect(message?.text).toContain('sin prevención')
    expect(message?.text).toContain('se produjo un deadlock y se resolvió')
  })
})
