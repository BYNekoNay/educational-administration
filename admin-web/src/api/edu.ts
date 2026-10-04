import request from './request'
import type {
  AdjustRequestVO,
  Attendance,
  AutoScheduleRequest,
  ClassGroup,
  ClassStudent,
  Classroom,
  ConflictCheckResult,
  Course,
  Enrollment,
  ExamLevel,
  ExamSignup,
  LeaveRequest,
  NotifyScope,
  PageData,
  PageParams,
  ParentBinding,
  Period,
  QuickAdjustPayload,
  RoomBooking,
  ScheduleLesson,
  ScheduleQueryParams,
  Student,
  TeacherInfo,
  UserInfo,
} from '@/types'

/** 学员管理 */
export const studentApi = {
  list: (params?: PageParams) => request.get<PageData<Student>>('/edu/students', { params }),
  getById: (id: number) => request.get<Student>(`/edu/students/${id}`),
  create: (data: Partial<Student>) => request.post<Student>('/edu/students', data),
  update: (id: number, data: Partial<Student>) => request.put<Student>(`/edu/students/${id}`, data),
  delete: (id: number) => request.delete<void>(`/edu/students/${id}`),
  parentOptions: () => request.get<UserInfo[]>('/edu/students/parent-options'),
  bindParent: (data: { studentId: number; parentUserId: number; relation?: string }) =>
    request.post<void>('/edu/students/bind-parent', data),
  listParents: (id: number) => request.get<ParentBinding[]>(`/edu/students/${id}/parents`),
  unbindParent: (id: number, parentUserId: number) =>
    request.delete<void>(`/edu/students/${id}/parents/${parentUserId}`),
  transfer: (id: number, targetClassId: number, fromClassId?: number) =>
    request.post<void>(`/edu/students/${id}/transfer`, null, {
      params: fromClassId != null ? { targetClassId, fromClassId } : { targetClassId },
    }),
  withdraw: (id: number) => request.post<void>(`/edu/students/${id}/withdraw`),
}

/** 课程管理 */
export const courseApi = {
  list: (params?: PageParams) => request.get<PageData<Course>>('/edu/courses', { params }),
  getById: (id: number) => request.get<Course>(`/edu/courses/${id}`),
  create: (data: Partial<Course>) => request.post<Course>('/edu/courses', data),
  update: (id: number, data: Partial<Course>) => request.put<Course>(`/edu/courses/${id}`, data),
  delete: (id: number) => request.delete<void>(`/edu/courses/${id}`),
}

/** 班级管理 */
export const classApi = {
  list: (params?: PageParams) => request.get<PageData<ClassGroup>>('/edu/classes', { params }),
  getById: (id: number) => request.get<ClassGroup>(`/edu/classes/${id}`),
  create: (data: Partial<ClassGroup>) => request.post<ClassGroup>('/edu/classes', data),
  update: (id: number, data: Partial<ClassGroup>) => request.put<ClassGroup>(`/edu/classes/${id}`, data),
  delete: (id: number) => request.delete<void>(`/edu/classes/${id}`),
  /** 班级学员列表 */
  students: (classId: number, params?: PageParams) =>
    request.get<PageData<ClassStudent>>(`/edu/classes/${classId}/students`, { params }),
  addStudent: (classId: number, data: Partial<ClassStudent>) =>
    request.post<void>(`/edu/classes/${classId}/students`, data),
  removeStudent: (classId: number, studentId: number) =>
    request.delete<void>(`/edu/classes/${classId}/students/${studentId}`),
}

/** 排课管理 */
export const scheduleApi = {
  list: (params?: ScheduleQueryParams) => request.get<PageData<ScheduleLesson>>('/edu/schedules', { params }),
  getById: (id: number) => request.get<ScheduleLesson>(`/edu/schedules/${id}`),
  create: (data: Partial<ScheduleLesson>) => request.post<ScheduleLesson>('/edu/schedules', data),
  update: (id: number, data: Partial<ScheduleLesson>) => request.put<ScheduleLesson>(`/edu/schedules/${id}`, data),
  delete: (id: number) => request.delete<void>(`/edu/schedules/${id}`),
  checkConflict: (data: Partial<ScheduleLesson>) =>
    request.post<ConflictCheckResult>('/edu/schedules/check-conflict', data),
  batchCreate: (data: Array<Partial<ScheduleLesson>>) => request.post<void>('/edu/schedules/batch', data),
  autoSchedule: (data: AutoScheduleRequest) => request.post<ScheduleLesson[]>('/edu/schedules/auto', data),
  /** 教务快速调课（拖拽）：{ lessonDate, startTime, endTime, reason } */
  quickAdjust: (id: number, data: QuickAdjustPayload) =>
    request.post<ScheduleLesson>(`/edu/schedules/${id}/quick-adjust`, data),
  /** 拖拽确认前查询影响范围：{ teacherId, teacherName, parentCount } */
  notifyScope: (id: number) => request.get<NotifyScope>(`/edu/schedules/${id}/notify-scope`),
}

/** 课节时段 */
export const periodApi = {
  list: () => request.get<Period[]>('/edu/periods'),
  create: (data: Partial<Period>) => request.post<Period>('/edu/periods', data),
  update: (id: number, data: Partial<Period>) => request.put<Period>(`/edu/periods/${id}`, data),
  delete: (id: number) => request.delete<void>(`/edu/periods/${id}`),
}

/** 教师下拉列表 */
export const teacherApi = {
  list: () => request.get<TeacherInfo[]>('/edu/teachers'),
}

/** 考勤管理 */
export const attendanceApi = {
  list: (params?: PageParams) => request.get<PageData<Attendance>>('/edu/attendances', { params }),
  getById: (id: number) => request.get<Attendance>(`/edu/attendances/${id}`),
  create: (data: Partial<Attendance>) => request.post<Attendance>('/edu/attendances', data),
  delete: (id: number) => request.delete<void>(`/edu/attendances/${id}`),
}

/** 教室管理 */
export const classroomApi = {
  list: (params?: PageParams) => request.get<PageData<Classroom>>('/edu/classrooms', { params }),
  getById: (id: number) => request.get<Classroom>(`/edu/classrooms/${id}`),
  create: (data: Partial<Classroom>) => request.post<Classroom>('/edu/classrooms', data),
  update: (id: number, data: Partial<Classroom>) => request.put<Classroom>(`/edu/classrooms/${id}`, data),
  delete: (id: number) => request.delete<void>(`/edu/classrooms/${id}`),
  roomBookings: (params?: PageParams) => request.get<PageData<RoomBooking>>('/edu/room-bookings', { params }),
  createRoomBooking: (data: Partial<RoomBooking>) => request.post<RoomBooking>('/edu/room-bookings', data),
}

/** 报名管理 */
export const enrollmentApi = {
  list: (params?: PageParams) => request.get<PageData<Enrollment>>('/edu/enrollments', { params }),
  getById: (id: number) => request.get<Enrollment>(`/edu/enrollments/${id}`),
  create: (data: Partial<Enrollment>) => request.post<Enrollment>('/edu/enrollments', data),
  update: (id: number, data: Partial<Enrollment>) => request.put<Enrollment>(`/edu/enrollments/${id}`, data),
  delete: (id: number) => request.delete<void>(`/edu/enrollments/${id}`),
  audit: (id: number, data: { status: number; remark?: string }) =>
    request.put<Enrollment>(`/edu/enrollments/${id}/audit`, null, { params: { status: data.status, remark: data.remark } }),
}

/** 调课管理 */
export const adjustApi = {
  list: (params?: PageParams) => request.get<PageData<AdjustRequestVO>>('/edu/schedule-adjust-requests', { params }),
  create: (data: Partial<ScheduleLesson>) => request.post<void>('/edu/schedule-adjust-requests', data),
  audit: (id: number, data: { status: number; remark?: string }) =>
    request.put<void>(`/edu/schedule-adjust-requests/${id}/audit`, null, { params: { status: data.status, remark: data.remark } }),
}

/** 考级管理 */
export const examApi = {
  levels: (params?: PageParams) => request.get<PageData<ExamLevel>>('/edu/exams/levels', { params }),
  createLevel: (data: Partial<ExamLevel>) => request.post<ExamLevel>('/edu/exams/levels', data),
  updateLevel: (id: number, data: Partial<ExamLevel>) => request.put<ExamLevel>(`/edu/exams/levels/${id}`, data),
  deleteLevel: (id: number) => request.delete<void>(`/edu/exams/levels/${id}`),
  signups: (params?: PageParams) => request.get<PageData<ExamSignup>>('/edu/exams/signups', { params }),
  signup: (data: Partial<ExamSignup>) => request.post<ExamSignup>('/edu/exams/signups', data),
  score: (id: number, data: Partial<ExamSignup>) => request.put<ExamSignup>(`/edu/exams/signups/${id}`, data),
}

/** 请假审核 */
export const leaveRequestApi = {
  list: (params?: PageParams) => request.get<PageData<LeaveRequest>>('/edu/leave-requests', { params }),
  audit: (id: number, data: { status: number; remark?: string }) =>
    request.put<LeaveRequest>(`/edu/leave-requests/${id}/audit`, null, { params: { status: data.status, remark: data.remark } }),
}