<template>
  <div class="data-table">
    <el-table
      :data="data"
      v-loading="loading"
      border
      stripe
      :row-key="rowKey"
      @sort-change="(e: any) => emit('sort-change', e)"
    >
      <el-table-column
        v-for="col in columns"
        :key="col.prop"
        :prop="col.prop"
        :label="col.label"
        :width="col.width"
        :min-width="col.minWidth"
        :sortable="col.sortable"
        :fixed="col.fixed"
        :align="col.align || 'left'"
      >
        <template #default="scope">
          <slot :name="'cell-' + col.prop" :row="scope.row" :value="scope.row[col.prop]">
            {{ col.formatter ? col.formatter(scope.row) : scope.row[col.prop] }}
          </slot>
        </template>
      </el-table-column>

      <el-table-column
        v-if="$slots.actions"
        label="操作"
        :width="actionsWidth"
        fixed="right"
        align="center"
      >
        <template #default="scope">
          <slot name="actions" :row="scope.row" />
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-if="showPagination"
      class="dt-pagination"
      :current-page="pageNum"
      :page-size="pageSize"
      :total="total"
      :page-sizes="pageSizes"
      layout="total, sizes, prev, pager, next, jumper"
      @current-change="(p: number) => emit('page-change', p, pageSize)"
      @size-change="(s: number) => emit('page-change', pageNum, s)"
    />
  </div>
</template>

<script setup lang="ts">
import { TableColumn } from './types'

const props = withDefaults(
  defineProps<{
    data?: any[]
    columns: TableColumn[]
    loading?: boolean
    total?: number
    pageNum?: number
    pageSize?: number
    showPagination?: boolean
    rowKey?: string
    actionsWidth?: string | number
    pageSizes?: number[]
  }>(),
  {
    data: () => [],
    loading: false,
    total: 0,
    pageNum: 1,
    pageSize: 10,
    showPagination: true,
    rowKey: 'id',
    actionsWidth: 160,
    pageSizes: () => [10, 20, 50, 100]
  }
)

const emit = defineEmits<{
  'page-change': [number, number]
  'sort-change': [any]
}>()
</script>

<style scoped>
.data-table {
  width: 100%;
}
.dt-pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
