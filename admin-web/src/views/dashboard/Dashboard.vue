<template>
  <div>
    <h2 style="margin-bottom: 20px">运营数据看板</h2>

    <!-- 统计卡片 -->
    <el-row :gutter="20">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="card-inner">
            <div class="card-label">在册学员</div>
            <div class="card-value" style="color:#409EFF">{{ cards.activeStudents ?? '--' }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="card-inner">
            <div class="card-label">本月课次</div>
            <div class="card-value" style="color:#67C23A">{{ cards.monthlyLessons ?? '--' }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="card-inner">
            <div class="card-label">本月营收</div>
            <div class="card-value" style="color:#E6A23C">¥{{ cards.monthlyRevenue ?? '--' }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="card-inner">
            <div class="card-label">到课率</div>
            <div class="card-value" style="color:#F56C6C">{{ cards.attendanceRate ?? '--' }}%</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表区域 -->
    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="12">
        <el-card shadow="hover">
          <div ref="lessonChartRef" style="height: 350px"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="hover">
          <div ref="revenueChartRef" style="height: 350px"></div>
        </el-card>
      </el-col>
    </el-row>
    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="24">
        <el-card shadow="hover">
          <div ref="attendanceChartRef" style="height: 350px"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 导出按钮 -->
    <el-card shadow="hover" style="margin-top: 20px">
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
          <h4>教师工作量</h4>
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
            <el-table-column prop="expected" label="应收" />
            <el-table-column prop="paid" label="实收" />
            <el-table-column label="收费率"><template #default="{ row }">{{ percent(row.rate) }}%</template></el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { dashboardApi, statisticsApi } from '@/api/auth'
import { ElMessage } from 'element-plus'
import ExportButton from '@/components/ExportButton.vue'

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
  const res = await dashboardApi.get()
  const d = res.data
  Object.assign(cards, d.cards)

  await nextTick()
  initLessonChart(d.charts.lessonTrend)
  initRevenueChart(d.charts.revenueTrend)
  initAttendanceChart(d.charts.attendanceTrend)
}

function initLessonChart(data: any[]) {
  if (!lessonChartRef.value) return
  lessonChart?.dispose()
  lessonChart = echarts.init(lessonChartRef.value)
  lessonChart.setOption({
    title: { text: '月度课时趋势', left: 'center' },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: data.map((d: any) => d.month) },
    yAxis: { type: 'value', name: '课时数' },
    series: [{ data: data.map((d: any) => d.count), type: 'line', smooth: true, areaStyle: { color: 'rgba(64,158,255,0.15)' }, itemStyle: { color: '#409EFF' } }]
  })
}

function initRevenueChart(data: any[]) {
  if (!revenueChartRef.value) return
  revenueChart?.dispose()
  revenueChart = echarts.init(revenueChartRef.value)
  revenueChart.setOption({
    title: { text: '月度营收趋势', left: 'center' },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: data.map((d: any) => d.month) },
    yAxis: { type: 'value', name: '金额(¥)' },
    series: [{ data: data.map((d: any) => d.amount), type: 'bar', itemStyle: { color: '#67C23A', borderRadius: [4, 4, 0, 0] } }]
  })
}

function initAttendanceChart(data: any[]) {
  if (!attendanceChartRef.value) return
  attendanceChart?.dispose()
  attendanceChart = echarts.init(attendanceChartRef.value)
  attendanceChart.setOption({
    title: { text: '到课率趋势', left: 'center' },
    tooltip: { trigger: 'axis', formatter: '{b}: {c}%' },
    xAxis: { type: 'category', data: data.map((d: any) => d.month) },
    yAxis: { type: 'value', name: '%', max: 100 },
    series: [{ data: data.map((d: any) => d.rate), type: 'line', smooth: true, areaStyle: { color: 'rgba(245,108,108,0.15)' }, itemStyle: { color: '#F56C6C' } }]
  })
}

function percent(value: unknown) {
  return Math.round((Number(value) || 0) * 100)
}

async function loadAnalytics() {
  try {
    const [teacher, loss, activity, profit, payment] = await Promise.all([
      statisticsApi.teacherWorkload(),
      statisticsApi.studentLoss(),
      statisticsApi.classActivity(),
      statisticsApi.courseProfit(),
      statisticsApi.paymentRate()
    ])
    teacherWorkload.value = teacher.data || []
    studentLoss.value = loss.data || []
    classActivity.value = activity.data || []
    courseProfit.value = profit.data || []
    paymentRate.value = payment.data || []
  } catch (error) {
    ElMessage.warning('部分运营统计加载失败')
  }
}

function handleResize() {
  lessonChart?.resize()
  revenueChart?.resize()
  attendanceChart?.resize()
}

onMounted(() => {
  loadDashboard()
  loadAnalytics()
})
window.addEventListener('resize', handleResize)
onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  lessonChart?.dispose()
  revenueChart?.dispose()
  attendanceChart?.dispose()
})
</script>

<style scoped>
.stat-card { text-align: center; }
.card-label { font-size: 14px; color: #909399; margin-bottom: 8px; }
.card-value { font-size: 32px; font-weight: bold; }
</style>
