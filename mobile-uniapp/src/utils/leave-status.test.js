import { describe, expect, it } from 'vitest'
import { canAudit, leaveStatusClass, leaveStatusText } from './leave-status'

describe('leaveStatusText', () => {
  it('maps 1/2/3 to 待审/通过/驳回', () => {
    expect(leaveStatusText(1)).toBe('待审')
    expect(leaveStatusText(2)).toBe('通过')
    expect(leaveStatusText(3)).toBe('驳回')
  })

  it('falls back to 未知 for unknown status', () => {
    expect(leaveStatusText(9)).toBe('未知')
    expect(leaveStatusText(null)).toBe('未知')
  })
})

describe('leaveStatusClass', () => {
  it('maps status to tag class names', () => {
    expect(leaveStatusClass(1)).toBe('tag-warning')
    expect(leaveStatusClass(2)).toBe('tag-success')
    expect(leaveStatusClass(3)).toBe('tag-danger')
  })

  it('falls back to tag-muted for unknown status', () => {
    expect(leaveStatusClass(0)).toBe('tag-muted')
  })
})

describe('canAudit', () => {
  it('only allows audit when status is 待审(1)', () => {
    expect(canAudit(1)).toBe(true)
    expect(canAudit(2)).toBe(false)
    expect(canAudit(3)).toBe(false)
  })
})
