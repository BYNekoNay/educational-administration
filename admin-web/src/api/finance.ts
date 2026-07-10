import request from './request'

/** 收费管理 */
export const paymentApi = {
  list: (params?: any) => request.get('/finance/payments', { params }),
  create: (data: any) => request.post('/finance/payments', data),
}

/** 退费管理 */
export const refundApi = {
  list: (params?: any) => request.get('/finance/refunds', { params }),
  create: (data: any) => request.post('/finance/refunds', data),
  audit: (id: number, data: any) => request.put(`/finance/refunds/${id}/audit`, data),
}

/** 课时账户 */
export const lessonAccountApi = {
  list: (params?: any) => request.get('/finance/lesson-accounts', { params }),
}

/** 课时流水 */
export const lessonFlowApi = {
  list: (params?: any) => request.get('/finance/lesson-flows', { params }),
}

/** 薪资管理 */
export const salaryApi = {
  list: (params?: any) => request.get('/finance/salaries', { params }),
  calculate: (data: any) => request.post('/finance/salaries', data),
  confirm: (id: number) => request.put(`/finance/salaries/${id}/confirm`),
  void: (id: number) => request.put(`/finance/salaries/${id}/void`),
  rules: (params?: any) => request.get('/finance/salaries/rules', { params }),
  createRule: (data: any) => request.post('/finance/salaries/rules', data),
  updateRule: (id: number, data: any) => request.put(`/finance/salaries/rules/${id}`, data),
  adjustments: (data: any) => request.post('/finance/salaries/adjustments', data),
}
