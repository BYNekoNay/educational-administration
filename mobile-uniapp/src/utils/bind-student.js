// 家长自助绑定学员的表单校验纯函数。
// 约束与后端 ParentSelfBindRequest 保持一致：姓名非空 ≤50，手机号 11 位且以 1[3-9] 开头。

export const PHONE_PATTERN = /^1[3-9]\d{9}$/

export const RELATION_OPTIONS = ['家长', '父亲', '母亲', '监护人', '其他']

/**
 * 校验绑定表单。
 * @param {{ studentName?: string, phone?: string }} form
 * @returns {string|null} 错误文案；通过时返回 null
 */
export function validateBindForm({ studentName, phone } = {}) {
  const name = String(studentName ?? '').trim()
  const tel = String(phone ?? '').trim()
  if (!name) return '请输入学员姓名'
  if (name.length > 50) return '学员姓名不能超过 50 个字符'
  if (!tel) return '请输入联系电话'
  if (!PHONE_PATTERN.test(tel)) return '请输入正确的 11 位手机号'
  return null
}

/**
 * 绑定成功提示文案。
 * @param {number} studentsCount 绑定后的孩子总数
 * @param {boolean} fromRegister 是否来自注册流程
 */
export function bindSuccessMessage(studentsCount, fromRegister = false) {
  if (fromRegister) return '注册并绑定成功'
  return studentsCount > 0 ? `绑定成功，当前共 ${studentsCount} 名孩子` : '绑定成功'
}

export default { validateBindForm, bindSuccessMessage, PHONE_PATTERN, RELATION_OPTIONS }
