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
        <el-button type="primary" @click="exportData('payments')">导出台账</el-button>
        <el-button type="success" @click="exportData('lesson-flows')">导出课时消耗</el-button>
        <el-button type="warning" @click="exportData('salaries')">导出薪资</el-button>
      </el-space>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { dashboardApi } from '@/api/auth'
import { ElMessage } from 'element-plus'

const cards = reactive<any>({ activeStudents: '--', monthlyLessons: '--', monthlyRevenue: '--', attendanceRate: '--' })

const lessonChartRef = ref<HTMLElement>()
const revenueChartRef = ref<HTMLElement>()
const attendanceChartRef = ref<HTMLElement>()
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

function exportData(type: string) {
  const url = `/api/export/${type}`
  const a = document.createElement('a')
  a.href = url
  a.click()
  ElMessage.success('正在导出...')
}

function handleResize() {
  lessonChart?.resize()
  revenueChart?.resize()
  attendanceChart?.resize()
}

onMounted(loadDashboard)
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
