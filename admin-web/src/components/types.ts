/**
 * 公共组件共享类型定义
 * 对应 docs/13-前端开发详细文档.md §3.6 规划的 6 个公共组件
 */

/** DataTable 列定义 */
export interface TableColumn {
  /** 字段名，对应行数据的 key */
  prop: string
  /** 列标题 */
  label: string
  /** 列宽（px 或 '120'），可选 */
  width?: string | number
  /** 最小列宽，可选 */
  minWidth?: string | number
  /** 是否可排序；传 'custom' 时由父组件处理服务端排序 */
  sortable?: boolean | 'custom'
  /** 固定列位置 */
  fixed?: boolean | 'left' | 'right'
  /** 对齐方式 */
  align?: 'left' | 'center' | 'right'
  /** 自定义格式化函数（不传则直接显示原始值） */
  formatter?: (row: any) => any
}

/** FormDialog 字段定义 */
export interface FormField {
  /** 字段名，对应 form 对象的 key */
  prop: string
  /** 字段标签 */
  label: string
  /** 输入类型，默认 input */
  type?: 'input' | 'textarea' | 'number' | 'select' | 'date' | 'switch'
  /** 占位提示 */
  placeholder?: string
  /** 是否必填（开启后保存前做空值校验） */
  required?: boolean
  /** 是否可清空（select 默认 true） */
  clearable?: boolean
  /** select 类型的选项 */
  options?: { label: string; value: any }[]
  /** number 类型的最小值 */
  min?: number
  /** number 类型的最大值 */
  max?: number
  /** date 类型的 value-format，默认 YYYY-MM-DD */
  valueFormat?: string
}

/** ScheduleCalendar 课次项 */
export interface ScheduleItem {
  /** 唯一标识 */
  id: number | string
  /** 课程/课次标题 */
  title: string
  /** 星期几：1=周一 … 7=周日 */
  dayOfWeek: number
  /** 开始时间 HH:mm */
  start: string
  /** 结束时间 HH:mm */
  end: string
  /** 左侧色条颜色，可选 */
  color?: string
  /** 地点（教室），可选 */
  location?: string
}
