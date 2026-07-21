<template>
  <template v-if="item.children && item.children.length">
    <el-sub-menu :index="String(item.id)">
      <template #title>
        <el-icon v-if="item.icon && iconMap[item.icon]"><component :is="iconMap[item.icon]" /></el-icon>
        <span>{{ item.menuName }}</span>
      </template>
      <MenuItem v-for="child in item.children" :key="child.id" :item="child" :icon-map="iconMap" />
    </el-sub-menu>
  </template>
  <template v-else>
    <el-menu-item :index="item.path || ''">
      <el-icon v-if="item.icon && iconMap[item.icon]"><component :is="iconMap[item.icon]" /></el-icon>
      <span>{{ item.menuName }}</span>
    </el-menu-item>
  </template>
</template>

<script setup lang="ts">
import type { Component } from 'vue'

interface MenuNode {
  id: number
  menuName: string
  icon: string | null
  path: string | null
  children?: MenuNode[]
}

defineProps<{
  item: MenuNode
  iconMap: Record<string, Component>
}>()
</script>
