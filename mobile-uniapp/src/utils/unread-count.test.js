import { describe, expect, it } from 'vitest'
import { formatBadge, shouldShowBadge } from './unread-count'

describe('formatBadge', () => {
  it('shows the number as string when 99 or below', () => {
    expect(formatBadge(0)).toBe('0')
    expect(formatBadge(3)).toBe('3')
    expect(formatBadge(99)).toBe('99')
  })

  it('shows 99+ when over 99', () => {
    expect(formatBadge(100)).toBe('99+')
    expect(formatBadge(256)).toBe('99+')
  })

  it('coerces non-numeric input to 0', () => {
    expect(formatBadge(undefined)).toBe('0')
    expect(formatBadge('abc')).toBe('0')
  })
})

describe('shouldShowBadge', () => {
  it('is true only when count is greater than 0', () => {
    expect(shouldShowBadge(0)).toBe(false)
    expect(shouldShowBadge(1)).toBe(true)
    expect(shouldShowBadge(100)).toBe(true)
  })

  it('coerces non-numeric input to false', () => {
    expect(shouldShowBadge(null)).toBe(false)
  })
})
