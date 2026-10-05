/**
 * 本地时区日期工具。
 *
 * 为什么需要它：`new Date().toISOString()` 返回的是 **UTC** 时间，而后端
 * `salaryMonth`（'yyyy-MM'）与 `LocalDate` 字段都按**服务端本地时区**序列化。
 * 在 UTC+8 下，每月 1 日 00:00–08:00 之间 `toISOString().slice(0, 7)` 会得到
 * 「上个月」，一键结算就会算错月份；取日期时同理会得到「前一天」。
 *
 * 因此凡是要与后端 LocalDate / 'yyyy-MM' 做比较或回填的地方，
 * 一律用这里的本地时区取值，不要直接用 toISOString()。
 */

function pad2(n: number): string {
  return String(n).padStart(2, '0')
}

/**
 * 当前**本地时区**日期，格式 'yyyy-MM-dd'
 * @param d 便于测试注入固定时间
 */
export function todayLocalDate(d: Date = new Date()): string {
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`
}

/**
 * 当前**本地时区**年月，格式 'yyyy-MM'（对齐后端 salaryMonth 入参格式）
 * @param d 便于测试注入固定时间
 */
export function currentYearMonth(d: Date = new Date()): string {
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}`
}
