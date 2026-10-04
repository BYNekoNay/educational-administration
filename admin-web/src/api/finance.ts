import request from './request'
import type {
  LessonAccount,
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
}

/** 课时账户 */
export const lessonAccountApi = {
  list: (params?: PageParams) => request.get<PageData<LessonAccount>>('/finance/lesson-accounts', { params }),
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