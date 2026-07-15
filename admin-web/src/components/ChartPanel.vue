<template>
  <div class="chart-panel">
    <div v-if="title" class="cp-title">{{ title }}</div>
    <div ref="el" class="cp-canvas" :style="{ height }"></div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import * as echarts from 'echarts'

const props = withDefaults(
  defineProps<{
    /** ECharts option 对象 */
    option: any
    height?: string
    title?: string
  }>(),
  { height: '320px', title: '' }
)

const el = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

function render() {
  if (!el.value) return
  if (!chart) chart = echarts.init(el.value)
  chart.setOption(props.option, true)
}

onMounted(render)
watch(() => props.option, () => nextTick(render), { deep: true })
onBeforeUnmount(() => chart?.dispose())
</script>

<style scoped>
.chart-panel {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 12px;
}
.cp-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
}
.cp-canvas {
  width: 100%;
}
</style>
