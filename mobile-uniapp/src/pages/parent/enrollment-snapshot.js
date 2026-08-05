export function mapEnrollmentSnapshot(snapshot) {
  const decisions = Array.isArray(snapshot?.courses) ? snapshot.courses : []
  const courses = []
  const classCountMap = {}
  const enrolledCourseIds = new Set()
  const classesByCourseId = {}
  const conflictsByCourseId = {}

  decisions.forEach(decision => {
    const course = decision?.course
    if (!course || course.id === undefined || course.id === null) return

    courses.push({
      ...course,
      activeEnrollmentStatus: decision.activeEnrollmentStatus ?? null,
      holdExpireTime: decision.holdExpireTime ?? null,
      holdExpired: Boolean(decision.holdExpired),
    })
    classCountMap[course.id] = Number.isFinite(decision.availableClassCount)
      ? decision.availableClassCount
      : (Array.isArray(decision.classes) ? decision.classes.length : 0)
    if (decision.enrolled) enrolledCourseIds.add(course.id)
    classesByCourseId[course.id] = Array.isArray(decision.classes) ? decision.classes : []

    const conflicts = {}
    ;(Array.isArray(decision.conflicts) ? decision.conflicts : []).forEach(conflict => {
      if (conflict?.classId !== undefined && conflict?.classId !== null) {
        conflicts[conflict.classId] = conflict
      }
    })
    conflictsByCourseId[course.id] = conflicts
  })

  return {
    studentId: snapshot?.studentId ?? null,
    snapshotAt: snapshot?.snapshotAt ?? null,
    snapshotExpiresAt: snapshot?.snapshotExpiresAt ?? null,
    versionToken: snapshot?.versionToken ?? '',
    courses,
    classCountMap,
    enrolledCourseIds,
    classesByCourseId,
    conflictsByCourseId,
  }
}

export function createSnapshotRequestGuard() {
  let generation = 0

  return {
    begin(studentId) {
      generation += 1
      return { generation, studentId }
    },
    isCurrent(request, currentStudentId) {
      return request.generation === generation && request.studentId === currentStudentId
    },
    invalidate() {
      generation += 1
    },
  }
}

export async function loadEnrollmentSnapshot({
  studentId,
  guard,
  fetchSnapshot,
  getCurrentStudentId,
  onSuccess,
  onError,
}) {
  const request = guard.begin(studentId)
  try {
    const response = await fetchSnapshot(studentId)
    if (!guard.isCurrent(request, getCurrentStudentId())) {
      return { status: 'stale' }
    }
    const state = mapEnrollmentSnapshot(response?.data ?? response)
    onSuccess(state)
    return { status: 'applied', state }
  } catch (error) {
    if (!guard.isCurrent(request, getCurrentStudentId())) {
      return { status: 'stale' }
    }
    onError(error)
    return { status: 'error', error }
  }
}
