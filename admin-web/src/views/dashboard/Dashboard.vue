<template>
  <div>
    <div class="page-header">
      <h2 class="page-title">运营数据看板</h2>
      <span class="page-sub">机构核心运营指标一览</span>
    </div>

    <!-- 统计卡片 -->
    <el-row :gutter="20">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="card-inner">
            <div class="card-label">在册学员</div>
            <div class="card-value" style="color: var(--brand-primary)">{{ cards.activeStudents ?? '--' }}</div>
          </div>
          <div class="card-accent" style="background: var(--brand-primary)"></div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="card-inner">
            <div class="card-label">本月课次</div>
            <div class="card-value" style="color: var(--color-success)">{{ cards.monthlyLessons ?? '--' }}</div>
          </div>
          <div class="card-accent" style="background: var(--color-success)"></div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="card-inner">
            <div class="card-label">本月营收</div>
            <div class="card-value" style="color: var(--accent-amber)">¥{{ formatMoney(cards.monthlyRevenue) }}</div>
          </div>
          <div class="card-accent" style="background: var(--accent-amber)"></div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="card-inner">
            <div class="card-label">到课率</div>
            <div class="card-value" style="color: var(--accent-rose)">{{ cards.attendanceRate ?? '--' }}%</div>
          </div>
          <div class="card-accent" style="background: var(--accent-rose)"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表区域 -->
    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="12">
        <el-card shadow="hover">
          <div class="chart-header">
            <span class="chart-title">月度课时趋势</span>
            <span class="chart-sub">近 6 月已完成课次数</span>
          </div>
          <div ref="lessonChartRef" style="height: 320px"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="hover">
          <div class="chart-header">
            <span class="chart-title">月度营收趋势</span>
            <span class="chart-sub">近 6 月每月收费金额（元）</span>
          </div>
          <div ref="revenueChartRef" style="height: 320px"></div>
        </el-card>
      </el-col>
    </el-row>
    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="24">
        <el-card shadow="hover">
          <div class="chart-header">
            <span class="chart-title">月度到课率趋势</span>
            <span class="chart-sub">近 6 月到课 + 迟到 / 应到课次</span>
          </div>
          <div ref="attendanceChartRef" style="height: 320px"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 导出按钮：后端 /export/* 仅放行 SUPER_ADMIN/FINANCE，其余角色隐藏避免必 403 -->
    <el-card v-if="canExport" shadow="hover" style="margin-top: 20px">
      <h4 style="margin: 0 0 12px 0">报表导出</h4>
      <el-space>
        <ExportButton url="/export/payments" filename="收费台账.xlsx" label="导出台账" />
        <ExportButton url="/export/lesson-flows" filename="课时消耗.xlsx" label="导出课时消耗" type="success" />
        <ExportButton url="/export/salaries" filename="薪资结算.xlsx" label="导出薪资" type="warning" />
      </el-space>
    </el-card>

    <el-card shadow="hover" style="margin-top: 20px">
      <h3 style="margin: 0 0 12px">多维运营分析</h3>
      <el-tabs v-model="analysisTab">
        <el-tab-pane label="教师工作量" name="teacher">
          <h4>教师工作量（本月）</h4>
          <el-table :data="teacherWorkload" border stripe>
            <el-table-column prop="teacherName" label="教师" />
            <el-table-column prop="lessonCount" label="完成课次" />
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="学员流失率" name="loss">
          <h4>学员流失率</h4>
          <el-table :data="studentLoss" border stripe>
            <el-table-column prop="month" label="月份" />
            <el-table-column prop="activeCount" label="月初在班" />
            <el-table-column prop="lossCount" label="退班人数" />
            <el-table-column label="流失率"><template #default="{ row }">{{ row.lossRate }}%</template></el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="流失预警（学员级）" name="risk">
          <RiskWarningPanel />
        </el-tab-pane>
        <el-tab-pane label="班级活跃度" name="class">
          <h4>班级活跃度</h4>
          <el-table :data="classActivity" border stripe>
            <el-table-column prop="className" label="班级" />
            <el-table-column prop="studentCount" label="学员数" />
            <el-table-column label="课次进度"><template #default="{ row }">{{ row.completedLessons }}/{{ row.totalLessons }}</template></el-table-column>
            <el-table-column label="到课率"><template #default="{ row }">{{ percent(row.attendanceRate) }}%</template></el-table-column>
            <el-table-column prop="lastLessonDate" label="最近上课" />
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="课程盈利" name="profit">
          <h4>课程盈利</h4>
          <el-table :data="courseProfit" border stripe>
            <el-table-column prop="courseName" label="课程" />
            <el-table-column prop="income" label="收入" />
            <el-table-column prop="refund" label="退费" />
            <el-table-column prop="netProfit" label="净收入" />
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="收费率" name="payment">
          <h4>收费率</h4>
          <el-table :data="paymentRate" border stripe>
            <el-table-column prop="courseName" label="课程" />
            <el-table-column prop="expected" label="应收报名数" />
            <el-table-column prop="paid" label="已缴费报名数" />
            <el-table-column label="收费率"><template #default="{ row }">{{ percent(row.rate) }}%</template></el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { dashboardApi, statisticsApi } from '@/api/auth'
import { ElMessage } from 'element-plus'
import { showError } from '@/utils/error'
import ExportButton from '@/components/ExportButton.vue'
import RiskWarningPanel from './components/RiskWarningPanel.vue'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
// 与后端 ExportController 的 @RequireRole({"SUPER_ADMIN","FINANCE"}) 对齐
const canExport = computed(() => ['SUPER_ADMIN', 'FINANCE'].includes(authStore.roleCode))

const cards = reactive<any>({ activeStudents: '--', monthlyLessons: '--', monthlyRevenue: '--', attendanceRate: '--' })

const lessonChartRef = ref<HTMLElement>()
const revenueChartRef = ref<HTMLElement>()
const attendanceChartRef = ref<HTMLElement>()
const analysisTab = ref('teacher')
const teacherWorkload = ref<any[]>([])
const studentLoss = ref<any[]>([])
const classActivity = ref<any[]>([])
const courseProfit = ref<any[]>([])
const paymentRate = ref<any[]>([])
let lessonChart: echarts.ECharts | null = null
let revenueChart: echarts.ECharts | null = null
let attendanceChart: echarts.ECharts | null = null

async function loadDashboard() {
  try {
    const res = await dashboardApi.get()
    const d = res.data || {}
    if (d.cards) Object.assign(cards, d.cards)

    await nextTick()
    const charts = d.charts || {}
    initLessonChart(charts.lessonTrend || [])
    initRevenueChart(charts.revenueTrend || [])
    initAttendanceChart(charts.attendanceTrend || [])
  } catch (e) {
    showError(e, '看板数据加载失败')
  }
}

function initLessonChart(data: any[]) {
  if (!lessonChartRef.value) return
  lessonChart?.dispose()
  lessonChart = echarts.init(lessonChartRef.value)
  lessonChart.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: data.map((d: any) => d.month) },
    yAxis: { type: 'value', name: '课时数' },
    series: [{ data: data.map((d: any) => d.count), type: 'line', smooth: true, areaStyle: { color: 'rgba(14,116,144,0.12)' }, itemStyle: { color: '#0E7490' } }]
  })
}

function initRevenueChart(data: any[]) {
  if (!revenueChartRef.value) return
  revenueChart?.dispose()
  revenueChart = echarts.init(revenueChartRef.value)
  revenueChart.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: data.map((d: any) => d.month) },
    yAxis: { type: 'value', name: '金额(¥)' },
    series: [{ data: data.map((d: any) => d.amount), type: 'bar', itemStyle: { color: '#10B981', borderRadius: [4, 4, 0, 0] } }]
  })
}

function initAttendanceChart(data: any[]) {
  if (!attendanceChartRef.value) return
  attendanceChart?.dispose()
  attendanceChart = echarts.init(attendanceChartRef.value)
  attendanceChart.setOption({
    tooltip: { trigger: 'axis', formatter: '{b}: {c}%' },
    xAxis: { type: 'category', data: data.map((d: any) => d.month) },
    yAxis: { type: 'value', name: '%', max: 100 },
    series: [{ data: data.map((d: any) => d.rate), type: 'line', smooth: true, areaStyle: { color: 'rgba(225,29,72,0.12)' }, itemStyle: { color: '#E11D48' } }]
  })
}

function percent(value: unknown) {
  return Math.round((Number(value) || 0) * 100)
}

/** 金额展示：非数值（含初始 '--'）原样兜底，数值加千分位 + 两位小数 */
function formatMoney(value: unknown): string {
  if (value === '--' || value === null || value === undefined || value === '') return '--'
  const n = Number(value)
  if (Number.isNaN(n)) return '--'
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function loadAnalytics() {
  // 五个统计接口相互独立：用 allSettled 隔离失败，避免单个接口异常拖空全部表格
  const results = await Promise.allSettled([
    statisticsApi.teacherWorkload(),
    statisticsApi.studentLoss(),
    statisticsApi.classActivity(),
    statisticsApi.courseProfit(),
    statisticsApi.paymentRate()
  ])
  const assign = (idx: number, target: { value: any[] }) => {
    const r = results[idx]
    if (r.status === 'fulfilled') target.value = r.value.data || []
  }
  assign(0, teacherWorkload)
  assign(1, studentLoss)
  assign(2, classActivity)
  assign(3, courseProfit)
  assign(4, paymentRate)
  if (results.some(r => r.status === 'rejected')) {
    ElMessage.warning('部分运营统计加载失败')
  }
}

let resizeTimer: ReturnType<typeof setTimeout> | null = null
function handleResize() {
  // 节流：resize 事件高频触发，三个图表连续 resize 会造成卡顿
  if (resizeTimer) return
  resizeTimer = setTimeout(() => {
    resizeTimer = null
    lessonChart?.resize()
    revenueChart?.resize()
    attendanceChart?.resize()
  }, 150)
}

onMounted(() => {
  loadDashboard()
  loadAnalytics()
  window.addEventListener('resize', handleResize)
})
onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  if (resizeTimer) { clearTimeout(resizeTimer); resizeTimer = null }
  lessonChart?.dispose()
  revenueChart?.dispose()
  attendanceChart?.dispose()
})
</script>

<style scoped>
.stat-card { text-align: center; position: relative; overflow: hidden; }
.card-label { font-size: var(--text-sm); color: var(--neutral-500); margin-bottom: 8px; letter-spacing: 0.02em; }
.card-value { font-size: var(--text-3xl); font-weight: var(--font-bold); }
.card-accent {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 4px;
}
.chart-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 4px;
}
.chart-title { font-size: var(--text-lg); font-weight: var(--font-semibold); color: var(--neutral-700); }
.chart-sub { font-size: var(--text-xs); color: var(--neutral-400); }

/* 页头：左强调条 + 标题 + 说明 */
.page-header {
  display: flex;
  align-items: baseline;
  gap: var(--space-3);
  margin-bottom: var(--space-6);
}
.page-title {
  margin: 0;
  font-size: var(--text-2xl);
  font-weight: var(--font-bold);
  color: var(--neutral-800);
  position: relative;
  padding-left: var(--space-3);
}
.page-title::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 4px;
  height: 1.1em;
  border-radius: var(--radius-full);
  background: var(--brand-gradient);
}
.page-sub { font-size: var(--text-sm); color: var(--neutral-400); }
</style>
