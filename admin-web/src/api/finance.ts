import request from './request'
import type {
  LessonAccount,
  LessonAdjustPayload,
  LessonFlow,
  PageData,
  PageParams,
  PaymentRecord,
  RefundRecord,
  SalaryAdjustment,
  SalaryBatchResult,
  SalaryRule,
  TeacherSalary,
} from '@/types'

/**
 * 财务域只读下拉（学员/课程/报名/教师）。
 *
 * 财务角色对 /api/edu/* 一律 403，原实现直接调教务接口导致每个财务页面首屏
 * 弹 3~4 次「无权访问该接口」、且 Promise.all 一拒全丢使下拉全空。
 * 这些接口只投影 id 与名称，不暴露手机号。
 */
export const financeOptionApi = {
  teachers: () => request.get<Array<{ id: number; name: string }>>('/finance/options/teachers'),
  courses: () => request.get<Array<{ id: number; name: string; price: number; totalLessons: number }>>('/finance/options/courses'),
  students: () => request.get<Array<{ id: number; name: string }>>('/finance/options/students'),
  enrollments: () => request.get<Array<{
    id: number; studentId: number; courseId: number; status: number; studentName: string; courseName: string
  }>>('/finance/options/enrollments'),
}

/** 收费管理 */
export const paymentApi = {
  list: (params?: PageParams) => request.get<PageData<PaymentRecord>>('/finance/payments', { params }),
  create: (data: Partial<PaymentRecord>) => request.post<PaymentRecord>('/finance/payments', data),
}

/** 退费管理 */
export const refundApi = {
  list: (params?: PageParams) => request.get<PageData<RefundRecord>>('/finance/refunds', { params }),
  create: (data: Partial<RefundRecord>) => request.post<RefundRecord>('/finance/refunds', data),
  audit: (id: number, data: { status: number; refundAmount?: number; remark?: string }) =>
    request.put<RefundRecord>(`/finance/refunds/${id}/audit`, data),
  /** 退费状态分布（待审/通过/驳回 + 通过金额合计） */
  stats: () => request.get<{ pending: number; approved: number; rejected: number; approvedAmount: number }>(
    '/finance/refunds/stats'),
}

/** 课时账户 */
export const lessonAccountApi = {
  list: (params?: PageParams) => request.get<PageData<LessonAccount>>('/finance/lesson-accounts', { params }),
  get: (id: number) => request.get<LessonAccount>(`/finance/lesson-accounts/${id}`),
  /** 人工调整课时（补课/扣课），返回新写入的课时流水 */
  adjust: (id: number, data: LessonAdjustPayload) =>
    request.post<LessonFlow>(`/finance/lesson-accounts/${id}/adjust`, data),
  /** 按 学员+课程 定位课时账户（收费页「调整课时」入口用，列表里只有学员名没有 accountId） */
  lookup: (studentId: number, courseId: number) =>
    request.get<LessonAccount>('/finance/lesson-accounts/lookup', { params: { studentId, courseId } }),
}

/** 课时流水 */
export const lessonFlowApi = {
  list: (params?: PageParams) => request.get<PageData<LessonFlow>>('/finance/lesson-flows', { params }),
}

/** 薪资管理 */
export const salaryApi = {
  list: (params?: PageParams) => request.get<PageData<TeacherSalary>>('/finance/salaries', { params }),
  calculate: (data: { salaryMonth: string; teacherId?: number; bonusAmount?: number }) =>
    request.post<TeacherSalary>('/finance/salaries', data),
  calculateBatch: (data: { salaryMonth: string; bonusAmount?: number }) =>
    request.post<SalaryBatchResult>('/finance/salaries/calculate-batch', data),
  confirm: (id: number) => request.put<TeacherSalary>(`/finance/salaries/${id}/confirm`),
  pay: (id: number) => request.put<TeacherSalary>(`/finance/salaries/${id}/pay`),
  void: (id: number) => request.put<TeacherSalary>(`/finance/salaries/${id}/void`),
  rules: (params?: PageParams) => request.get<PageData<SalaryRule>>('/finance/salaries/rules', { params }),
  createRule: (data: Partial<SalaryRule>) => request.post<SalaryRule>('/finance/salaries/rules', data),
  updateRule: (id: number, data: Partial<SalaryRule>) =>
    request.put<SalaryRule>(`/finance/salaries/rules/${id}`, data),
  adjustments: (data: { salaryId: number; adjustAmount: number; reason: string }) =>
    request.post<SalaryAdjustment>('/finance/salaries/adjustments', data),
}