<template>
  <div>
    <h3>操作日志</h3>
    <el-table :data="[]" border stripe>
      <el-table-column prop="operatorId" label="操作人" />
      <el-table-column prop="module" label="模块" />
      <el-table-column prop="operation" label="操作" />
      <el-table-column prop="createTime" label="时间" />
    </el-table>
  </div>
</template>
