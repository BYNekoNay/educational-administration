// 请假状态相关纯函数。状态语义：1待审 / 2通过 / 3驳回（与后端 LeaveRequest、parent/teacher 展示共用）

const LEAVE_STATUS_TEXT = {
  1: '待审',
  2: '通过',
  3: '驳回',
}

const LEAVE_STATUS_CLASS = {
  1: 'tag-warning',
  2: 'tag-success',
  3: 'tag-danger',
}

// status: 1待审 / 2通过 / 3驳回
export function leaveStatusText(status) {
  return LEAVE_STATUS_TEXT[status] || '未知'
}

// 返回标签样式类名（沿用 content.css 的 tag-* 约定）
export function leaveStatusClass(status) {
  return LEAVE_STATUS_CLASS[status] || 'tag-muted'
}

// 仅 status=1（待审）可操作审批
export function canAudit(status) {
  return status === 1
}

export default { leaveStatusText, leaveStatusClass, canAudit }
