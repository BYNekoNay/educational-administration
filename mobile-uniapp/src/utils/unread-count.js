// 消息未读数角标相关纯函数

// 角标文案：超过 99 显示 "99+"，否则显示数字字符串
export function formatBadge(n) {
  const num = Number(n) || 0
  if (num > 99) return '99+'
  return String(num)
}

// 是否应当展示角标（未读数 > 0）
export function shouldShowBadge(n) {
  const num = Number(n) || 0
  return num > 0
}

export default { formatBadge, shouldShowBadge }
