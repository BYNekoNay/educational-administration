/**
 * 管理后台领域类型定义。
 *
 * 与后端实体 / VO 字段保持一致（后端实体见各 modules 下的 entity 目录），
 * 供 API 层与页面组件复用以替代 any。
 */

/** 后端统一响应体 { code, message, data }（响应拦截器已解包 response.data） */
export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
}

/** 分页结构（后端 PageResult） */
export interface PageData<T> {
  records: T[]
  total: number
  pageNum?: number
  pageSize?: number
}

/** 通用分页 / 筛选查询参数 */
export interface PageParams {
  pageNum?: number
  pageSize?: number
  keyword?: string
  sortField?: string
  sortOrder?: string
  [key: string]: string | number | boolean | undefined
}

// ==================== 学员 / 课程 / 班级 ====================

export interface Student {
  id?: number
  name: string
  gender?: number | null
  birthday?: string | null
  school?: string | null
  contactPhone?: string | null
  /** 1=正常，4=已退班 */
  status?: number
  parentName?: string | null
  createTime?: string
}

export interface Course {
  id?: number
  name: string
  category?: string | null
  totalLessons: number
  lessonDuration?: number | null
  price: number
  status?: number
  createTime?: string
}

export interface ClassGroup {
  id?: number
  courseId: number | null
  className: string
  teacherId: number | null
  maxStudentCount?: number | null
  startDate?: string | null
  status?: number
  courseName?: string
  teacherName?: string
  currentStudentCount?: number
}

export interface ClassStudent {
  id?: number
  classId?: number
  studentId?: number
  joinTime?: string
  /** 1=在班，2=已转出，3=已退出 */
  status?: number
  studentName?: string
}

// ==================== 报名 / 财务 ====================

export interface Enrollment {
  id?: number
  studentId: number | null
  parentUserId?: number | null
  courseId: number | null
  classId?: number | null
  /** 1=待审核，2=待缴费，3=已完成，4=已拒绝，5=已失效，6=已退费 */
  status?: number
  auditorId?: number | null
  auditRemark?: string | null
  holdExpireTime?: string | null
  studentName?: string
  courseName?: string
  className?: string
  parentName?: string
  auditorName?: string
  createTime?: string
}

export interface PaymentRecord {
  id?: number
  enrollmentId: number | null
  studentId?: number | null
  courseId?: number | null
  lessonCount: number | null
  amount: number | null
  payType?: number
  payTime?: string | null
  operatorId?: number | null
  operatorRole?: string | null
  remark?: string | null
  studentName?: string
  courseName?: string
  createTime?: string
}

export interface RefundRecord {
  id?: number
  studentId: number | null
  paymentRecordId?: number | null
  enrollmentId: number | null
  applicantId?: number | null
  applicantRole?: string
  auditorId?: number | null
  amount?: number | null
  lessonCount?: number | null
  /** 1=待审核，2=已通过，3=已拒绝 */
  status?: number
  studentName?: string
  applicantName?: string
  createTime?: string
}

export interface LessonAccount {
  id?: number
  studentId: number | null
  courseId: number | null
  totalLessons?: number
  remainingLessons?: number
  expireDate?: string | null
  version?: number
  studentName?: string
  courseName?: string
}

export interface LessonFlow {
  id?: number
  accountId?: number
  studentId?: number
  lessonId?: number | null
  /** 1-缴费，2-考勤消费，3-回冲，4-退费 */
  sourceType?: number
  sourceId?: number | null
  changeAmount?: number
  /** 1-增加，2-减少，3-退费，4-调整 */
  changeType?: number
  beforeBalance?: number
  afterBalance?: number
  remark?: string | null
  studentName?: string
  courseName?: string
  createTime?: string
}

export interface SalaryRule {
  id?: number
  teacherId: number | null
  courseId: number | null
  lessonUnitPrice: number | null
  substituteRate?: number
  teacherName?: string
  courseName?: string
}

export interface TeacherSalary {
  id?: number
  teacherId: number | null
  salaryMonth: string
  lessonCount?: number
  substituteCount?: number
  baseAmount?: number
  substituteAmount?: number
  bonusAmount?: number
  totalAmount?: number
  /** 1=待确认，2=已确认，3=已发放，4=已撤销 */
  status?: number
  calcSnapshotTime?: string
  teacherName?: string
  createTime?: string
}

// ==================== 排课 / 考勤 / 学情 ====================

export interface ScheduleLesson {
  id?: number
  classId: number | null
  teacherId: number | null
  classroomId: number | null
  lessonDate: string
  startTime: string
  endTime: string
  /** 1=待上课，2=已完成，3=已取消，4=已调课 */
  status?: number
  sourceLessonId?: number | null
  periodId?: number | null
  periodCount?: number
  className?: string
  teacherName?: string
  classroomName?: string
  courseName?: string
  courseId?: number
  leaveStatus?: number | null
  periodName?: string
}

export interface Classroom {
  id?: number
  name: string
  capacity: number
  campus?: string | null
  status?: number
}

export interface Period {
  id?: number
  name: string
  slotOrder: number
  startTime: string
  endTime: string
}

export interface Attendance {
  id?: number
  lessonId: number | null
  studentId: number | null
  /** 1=到课，2=迟到，3=请假，4=缺勤 */
  status?: number
  deductLessons?: number | null
  checkTime?: string | null
  remark?: string | null
  studentName?: string
  lessonInfo?: string
}

export interface Homework {
  id?: number
  lessonId: number | null
  teacherId?: number | null
  content?: string | null
  attachmentUrl?: string | null
  createTime?: string
}

export interface LearningRecord {
  id?: number
  lessonId: number | null
  studentId: number | null
  teacherComment?: string | null
  growthTag?: string | null
  studentName?: string
}

export interface LeaveRequest {
  id?: number
  studentId: number | null
  parentUserId?: number | null
  lessonDate?: string
  scheduleId?: number | null
  reason?: string | null
  /** 1=待审核，2=已通过，3=已拒绝 */
  status?: number
  auditUserId?: number | null
  auditRemark?: string | null
  studentName?: string
  courseName?: string
}

// ==================== 系统 / 权限 / 运营 ====================

export interface UserInfo {
  id?: number
  username: string
  realName?: string
  phone?: string | null
  roleCode: string
  status?: number
  lastLoginTime?: string | null
  createTime?: string
  /** 教师可授课程 ID（/edu/teachers 回填） */
  specialtyCourseIds?: number[]
  /** 教师可授课程明细（/edu/teachers 回填） */
  specialties?: Course[]
}

export interface RoleInfo {
  id: number
  roleCode: string
  roleName: string
}

/** 教师下拉项（GET /edu/teachers，User 实体回填 specialties） */
export type TeacherInfo = UserInfo

/** 学员已绑定家长展示对象（后端 ParentBindingVO） */
export interface ParentBinding {
  id?: number
  parentUserId?: number
  realName?: string
  username?: string
  phone?: string | null
  /** 与学员的关系（父亲/母亲等） */
  relation?: string | null
}

/** 薪资调整记录 */
export interface SalaryAdjustment {
  id?: number
  teacherSalaryId: number
  adjustAmount: number
  reason: string
  operatorId?: number
  createTime?: string
}

/** 批量结算结果 */
export interface SalaryBatchResult {
  salaryMonth?: string
  totalTeachers?: number
  successCount?: number
  failedCount?: number
  totalAmount?: number
  errors?: Array<{ teacherId?: number; teacherName?: string; reason?: string }>
}

/** 看板指标卡 */
export interface DashboardCards {
  activeStudents?: number
  monthlyLessons?: number
  monthlyRevenue?: number
  attendanceRate?: number
}

/** 看板：月度课时趋势点（后端 Map: month/count） */
export interface LessonTrendPoint {
  month: string
  count: number
}

/** 看板：月度营收趋势点（后端 Map: month/amount） */
export interface RevenueTrendPoint {
  month: string
  amount: number
}

/** 看板：月度到课率趋势点（后端 Map: month/rate） */
export interface AttendanceTrendPoint {
  month: string
  rate: number
}

/** 看板数据（后端 Map：cards 指标卡 + charts 图表数据） */
export interface DashboardStats {
  cards?: DashboardCards
  charts?: {
    lessonTrend?: LessonTrendPoint[]
    revenueTrend?: RevenueTrendPoint[]
    attendanceTrend?: AttendanceTrendPoint[]
  }
  [key: string]: unknown
}

// ==================== 流失预警 ====================

/** 流失预警汇总（后端 RiskSummaryVO） */
export interface RiskSummary {
  total?: number
  high?: number
  medium?: number
  low?: number
  byLevel?: Array<{ level?: string; count?: number }>
}

/** 流失预警名单行（后端 RiskStudentVO） */
export interface RiskStudentRow {
  studentId?: number
  studentName?: string
  classId?: number | null
  className?: string
  courseId?: number | null
  courseName?: string
  riskScore?: number
  /** HIGH / MEDIUM / LOW */
  riskLevel?: string
  f1?: number
  f2?: number
  f3?: number
  f4?: number
  f5?: number
  /** 最近到课/迟到日期（null=从未到课） */
  lastAttendDate?: string | null
  scheduledCount28d?: number
  absentCount28d?: number
  absenceRate28d?: number
  remainingLessons?: number
  totalLessons?: number
  expireDate?: string | null
  daysToExpire?: number
  suggestedAction?: string
  /** 0-待跟进 1-已跟进 2-暂不跟进（null=从未标记） */
  followUpStatus?: number | null
  followUpRemark?: string | null
}

// ==================== 请求载荷 ====================

/** 新建用户（后端 CreateUserRequest） */
export interface CreateUserPayload {
  username: string
  password: string
  realName: string
  phone?: string
  roleCode: string
  specialtyCourseIds?: number[]
}

/** 更新用户（后端 UpdateUserRequest，字段均可选） */
export interface UpdateUserPayload {
  username?: string
  realName?: string
  phone?: string
  roleCode?: string
  specialtyCourseIds?: number[]
}

/** 角色权限查询结果 */
export interface RolePermissionResult {
  roleId?: number
  roleCode: string
  permissionCodes: string[]
}

export interface MenuItem {
  id: number
  parentId: number
  menuName: string
  icon: string | null
  path: string | null
  permissionCode: string | null
  sortOrder: number
  visible: number
  children?: MenuItem[]
}

/** 操作日志（后端 OperationLog 实体 + operatorName 回填） */
export interface OperationLog {
  id: number
  operatorId: number
  operatorName?: string
  module: string
  operation: string
  ip?: string | null
  createTime: string
}

export interface PermissionItem {
  id?: number
  permissionCode: string
  path?: string | null
  /** 1=菜单，2=接口 */
  type: number
}

export interface Organization {
  id?: number
  orgName: string
  campus?: string | null
  contactPhone?: string | null
  address?: string | null
}

export interface Notice {
  id?: number
  title: string
  content?: string | null
  receiverType?: string
  receiverId?: number | null
  noticeType?: number
  publishTime?: string | null
  createTime?: string
}

export interface NotificationItem {
  id?: number
  userId?: number
  type: string
  title: string
  content?: string | null
  relatedId?: number | null
  isRead?: number
  createTime?: string
}

export interface ExamLevel {
  id?: number
  name: string
  levelName: string
  examDate?: string | null
  fee?: number
}

export interface ExamSignup {
  id?: number
  examId: number
  studentId: number
  score?: number | null
  certificateNo?: string | null
  certificateFileUrl?: string | null
  /** 1=已报名，2=已考试，3=已发证 */
  status?: number
  studentName?: string
  examName?: string
}

/** 考级报名表单模型（examId/studentId 未选择时为 null，仅用于弹窗表单，非接口返回） */
export interface ExamSignupForm extends Omit<Partial<ExamSignup>, 'examId' | 'studentId'> {
  examId: number | null
  studentId: number | null
}

export interface LoginResult {
  token: string
  userId: number
  username: string
  realName: string
  roleCode: string
  permissions: string[]
}

// ==================== 请求载荷 / 列表 VO ====================

export interface RoomBooking {
  id?: number
  classroomId: number | null
  startTime: string
  endTime: string
  purpose?: string | null
  applicantId?: number | null
  classroomName?: string
}

/** 调课申请列表 VO（含课次关联信息） */
export interface AdjustRequestVO {
  id?: number
  lessonId?: number
  expectTime?: string | null
  reason?: string | null
  status?: number
  auditRemark?: string | null
  createTime?: string
  lessonDate?: string
  startTime?: string
  endTime?: string
  className?: string
  courseName?: string
  classroomName?: string
  teacherName?: string
  periodId?: number | null
  periodCount?: number
  periodName?: string
}

/** 自动排课请求（后端 AutoScheduleRequest） */
export interface AutoScheduleRequest {
  classId: number
  teacherId: number
  classroomId?: number | null
  startDate: string
  endDate: string
  startTime: string
  endTime: string
  weekdays?: number[]
  lessonCount: number
}

/** 拖拽快速调课请求（后端 QuickAdjustRequest） */
export interface QuickAdjustPayload {
  lessonDate: string
  startTime: string
  endTime: string
  reason?: string
}

/** 排课冲突检测结果 */
export interface ConflictCheckResult {
  conflicts: string[]
  hasConflict: boolean
}

/** 快速调课通知影响范围 */
export interface NotifyScope {
  teacherId?: number | null
  teacherName?: string
  parentCount?: number
}

/** 分页 + 筛选的排课查询参数 */
export interface ScheduleQueryParams extends PageParams {
  courseId?: number
  classId?: number
  teacherId?: number
  classroomId?: number
  status?: number
  dateFrom?: string
  dateTo?: string
}