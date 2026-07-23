import request from './request'

/** 学员管理 */
export const studentApi = {
  list: (params?: any) => request.get('/edu/students', { params }),
  getById: (id: number) => request.get(`/edu/students/${id}`),
  create: (data: any) => request.post('/edu/students', data),
  update: (id: number, data: any) => request.put(`/edu/students/${id}`, data),
  delete: (id: number) => request.delete(`/edu/students/${id}`),
  parentOptions: () => request.get('/edu/students/parent-options'),
  bindParent: (data: any) => request.post('/edu/students/bind-parent', data),
  listParents: (id: number) => request.get(`/edu/students/${id}/parents`),
  unbindParent: (id: number, parentUserId: number) =>
    request.delete(`/edu/students/${id}/parents/${parentUserId}`),
  transfer: (id: number, targetClassId: number, fromClassId?: number) =>
    request.post(`/edu/students/${id}/transfer`, null, {
      params: fromClassId != null ? { targetClassId, fromClassId } : { targetClassId },
    }),
  withdraw: (id: number) => request.post(`/edu/students/${id}/withdraw`),
}

/** 课程管理 */
export const courseApi = {
  list: (params?: any) => request.get('/edu/courses', { params }),
  getById: (id: number) => request.get(`/edu/courses/${id}`),
  create: (data: any) => request.post('/edu/courses', data),
  update: (id: number, data: any) => request.put(`/edu/courses/${id}`, data),
  delete: (id: number) => request.delete(`/edu/courses/${id}`),
}

/** 班级管理 */
export const classApi = {
  list: (params?: any) => request.get('/edu/classes', { params }),
  getById: (id: number) => request.get(`/edu/classes/${id}`),
  create: (data: any) => request.post('/edu/classes', data),
  update: (id: number, data: any) => request.put(`/edu/classes/${id}`, data),
  delete: (id: number) => request.delete(`/edu/classes/${id}`),
  /** 班级学员列表 */
  students: (classId: number, params?: any) =>
    request.get(`/edu/classes/${classId}/students`, { params }),
  addStudent: (classId: number, data: any) =>
    request.post(`/edu/classes/${classId}/students`, data),
  removeStudent: (classId: number, studentId: number) =>
    request.delete(`/edu/classes/${classId}/students/${studentId}`),
}

/** 排课管理 */
export const scheduleApi = {
  list: (params?: any) => request.get('/edu/schedules', { params }),
  getById: (id: number) => request.get(`/edu/schedules/${id}`),
  create: (data: any) => request.post('/edu/schedules', data),
  update: (id: number, data: any) => request.put(`/edu/schedules/${id}`, data),
  delete: (id: number) => request.delete(`/edu/schedules/${id}`),
  checkConflict: (data: any) => request.post('/edu/schedules/check-conflict', data),
  batchCreate: (data: any) => request.post('/edu/schedules/batch', data),
  autoSchedule: (data: any) => request.post('/edu/schedules/auto', data),
}

/** 课节时段 */
export const periodApi = {
  list: () => request.get('/edu/periods'),
  create: (data: any) => request.post('/edu/periods', data),
  update: (id: number, data: any) => request.put(`/edu/periods/${id}`, data),
  delete: (id: number) => request.delete(`/edu/periods/${id}`),
}

/** 教师下拉列表 */
export const teacherApi = {
  list: () => request.get('/edu/teachers'),
}

/** 考勤管理 */
export const attendanceApi = {
  list: (params?: any) => request.get('/edu/attendances', { params }),
  getById: (id: number) => request.get(`/edu/attendances/${id}`),
  create: (data: any) => request.post('/edu/attendances', data),
  delete: (id: number) => request.delete(`/edu/attendances/${id}`),
}

/** 教室管理 */
export const classroomApi = {
  list: (params?: any) => request.get('/edu/classrooms', { params }),
  getById: (id: number) => request.get(`/edu/classrooms/${id}`),
  create: (data: any) => request.post('/edu/classrooms', data),
  update: (id: number, data: any) => request.put(`/edu/classrooms/${id}`, data),
  delete: (id: number) => request.delete(`/edu/classrooms/${id}`),
  roomBookings: (params?: any) => request.get('/edu/room-bookings', { params }),
  createRoomBooking: (data: any) => request.post('/edu/room-bookings', data),
}

/** 报名管理 */
export const enrollmentApi = {
  list: (params?: any) => request.get('/edu/enrollments', { params }),
  getById: (id: number) => request.get(`/edu/enrollments/${id}`),
  create: (data: any) => request.post('/edu/enrollments', data),
  update: (id: number, data: any) => request.put(`/edu/enrollments/${id}`, data),
  delete: (id: number) => request.delete(`/edu/enrollments/${id}`),
  audit: (id: number, data: any) => request.put(`/edu/enrollments/${id}/audit`, null, { params: { status: data.status, remark: data.remark } }),
}

/** 调课管理 */
export const adjustApi = {
  list: (params?: any) => request.get('/edu/schedule-adjust-requests', { params }),
  create: (data: any) => request.post('/edu/schedule-adjust-requests', data),
  audit: (id: number, data: any) => request.put(`/edu/schedule-adjust-requests/${id}/audit`, null, { params: { status: data.status, remark: data.remark } }),
}

/** 考级管理 */
export const examApi = {
  levels: (params?: any) => request.get('/edu/exams/levels', { params }),
  createLevel: (data: any) => request.post('/edu/exams/levels', data),
  updateLevel: (id: number, data: any) => request.put(`/edu/exams/levels/${id}`, data),
  deleteLevel: (id: number) => request.delete(`/edu/exams/levels/${id}`),
  signups: (params?: any) => request.get('/edu/exams/signups', { params }),
  signup: (data: any) => request.post('/edu/exams/signups', data),
  score: (id: number, data: any) => request.put(`/edu/exams/signups/${id}`, data),
}

/** 请假审核 */
export const leaveRequestApi = {
  list: (params?: any) => request.get('/edu/leave-requests', { params }),
  audit: (id: number, data: any) => request.put(`/edu/leave-requests/${id}/audit`, null, { params: { status: data.status, remark: data.remark } }),
}
