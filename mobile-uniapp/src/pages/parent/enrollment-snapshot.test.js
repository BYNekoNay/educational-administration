import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import {
  createSnapshotRequestGuard,
  loadEnrollmentSnapshot,
  mapEnrollmentSnapshot,
} from './enrollment-snapshot'

describe('mapEnrollmentSnapshot', () => {
  it('maps courses, counts, enrollment, capacities and conflicts from one response', () => {
    const mapped = mapEnrollmentSnapshot({
      studentId: 9,
      snapshotAt: '2026-08-05T20:00:00',
      versionToken: 'v1-test',
      courses: [
        {
          course: { id: 10, name: '钢琴' },
          enrolled: true,
          activeEnrollmentStatus: 2,
          holdExpireTime: '2026-08-06T20:00:00',
          holdExpired: false,
          availableClassCount: 1,
          classes: [
            { id: 101, maxStudentCount: 12, currentStudentCount: 5 },
          ],
          conflicts: [],
        },
        {
          course: { id: 20, name: '美术' },
          enrolled: false,
          availableClassCount: 1,
          classes: [
            { id: 201, maxStudentCount: 0, currentStudentCount: 3 },
          ],
          conflicts: [
            { classId: 201, conflictClassName: '周末钢琴班', description: '周六时间重叠' },
          ],
        },
      ],
    })

    expect(mapped.courses.map(course => course.id)).toEqual([10, 20])
    expect(mapped.courses[0]).toMatchObject({
      activeEnrollmentStatus: 2,
      holdExpireTime: '2026-08-06T20:00:00',
      holdExpired: false,
    })
    expect(mapped.classCountMap).toEqual({ 10: 1, 20: 1 })
    expect([...mapped.enrolledCourseIds]).toEqual([10])
    expect(mapped.classesByCourseId[20][0]).toMatchObject({
      maxStudentCount: 0,
      currentStudentCount: 3,
    })
    expect(mapped.conflictsByCourseId[20][201]).toMatchObject({
      conflictClassName: '周末钢琴班',
    })
    expect(mapped.versionToken).toBe('v1-test')
  })

  it('maps a missing or empty snapshot to empty UI state', () => {
    const mapped = mapEnrollmentSnapshot(null)

    expect(mapped.courses).toEqual([])
    expect(mapped.classCountMap).toEqual({})
    expect(mapped.enrolledCourseIds.size).toBe(0)
    expect(mapped.classesByCourseId).toEqual({})
    expect(mapped.conflictsByCourseId).toEqual({})
  })
})

describe('createSnapshotRequestGuard', () => {
  it('rejects an older response after the selected student changes', () => {
    const guard = createSnapshotRequestGuard()
    const firstStudentRequest = guard.begin(1)
    const secondStudentRequest = guard.begin(2)

    expect(guard.isCurrent(firstStudentRequest, 2)).toBe(false)
    expect(guard.isCurrent(secondStudentRequest, 2)).toBe(true)
  })
})

describe('loadEnrollmentSnapshot', () => {
  it('reports a current network failure without applying partial state', async () => {
    const guard = createSnapshotRequestGuard()
    const applied = []
    const failures = []

    const result = await loadEnrollmentSnapshot({
      studentId: 2,
      guard,
      fetchSnapshot: () => Promise.reject(new Error('network down')),
      getCurrentStudentId: () => 2,
      onSuccess: state => applied.push(state),
      onError: error => failures.push(error.message),
    })

    expect(result.status).toBe('error')
    expect(applied).toEqual([])
    expect(failures).toEqual(['network down'])
  })

  it('drops a response that arrives after switching students', async () => {
    const guard = createSnapshotRequestGuard()
    const applied = []
    let resolveFirst
    const firstResponse = new Promise(resolve => { resolveFirst = resolve })
    let currentStudentId = 1

    const firstLoad = loadEnrollmentSnapshot({
      studentId: 1,
      guard,
      fetchSnapshot: () => firstResponse,
      getCurrentStudentId: () => currentStudentId,
      onSuccess: state => applied.push(state),
      onError: () => {},
    })
    currentStudentId = 2
    const secondLoad = loadEnrollmentSnapshot({
      studentId: 2,
      guard,
      fetchSnapshot: () => Promise.resolve({ data: { studentId: 2, courses: [] } }),
      getCurrentStudentId: () => currentStudentId,
      onSuccess: state => applied.push(state),
      onError: () => {},
    })
    resolveFirst({ data: { studentId: 1, courses: [{ course: { id: 10 } }] } })

    const [firstResult, secondResult] = await Promise.all([firstLoad, secondLoad])

    expect(firstResult.status).toBe('stale')
    expect(secondResult.status).toBe('applied')
    expect(applied).toHaveLength(1)
    expect(applied[0].studentId).toBe(2)
  })
})

describe('enrollment page integration', () => {
  it('uses the single snapshot request and submits its version', () => {
    const pageSource = readFileSync(
      fileURLToPath(new URL('./enrollment.vue', import.meta.url)),
      'utf8',
    )

    expect(pageSource).toContain('/api/parent/enrollments/snapshot?studentId=')
    expect(pageSource).toContain("header: { 'If-Match': snapshotVersion }")
    expect(pageSource).not.toContain('/api/parent/enrollments/check/')
    expect(pageSource).not.toContain('/api/parent/enrollments/conflicts/')
    expect(pageSource).not.toContain('/api/parent/courses/${course.id}/classes')
  })
})
