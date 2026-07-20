import { mount } from '@vue/test-utils'
import type { Component } from 'vue'

const loadingDirective = {
  mounted() {},
  updated() {},
  unmounted() {},
}

const baseStubs: Record<string, any> = {
  'el-table': true,
  'el-table-column': true,
  'el-tag': true,
  'el-pagination': true,
  'el-input': true,
  'el-input-number': true,
  'el-select': true,
  'el-option': true,
  'el-date-picker': true,
  'el-time-picker': true,
  'el-switch': true,
  'el-checkbox': true,
  'el-checkbox-group': { template: '<div><slot /></div>' },
  'el-radio': true,
  'el-popconfirm': true,
  'el-icon': true,
  'el-tooltip': true,
  'el-tabs': { template: '<div><slot /></div>' },
  'el-tab-pane': { template: '<div><slot /></div>' },
  'el-card': { template: '<div class="el-card"><slot /></div>' },
  'el-row': { template: '<div><slot /></div>' },
  'el-col': { template: '<div><slot /></div>' },
  'el-space': { template: '<div><slot /></div>' },
  'el-form': { template: '<div><slot /></div>' },
  'el-form-item': { template: '<div><span v-if="$attrs.label">{{ $attrs.label }}</span><slot /></div>', inheritAttrs: false },
  'el-dialog': { template: '<div v-if="modelValue" class="el-dialog"><slot /><slot name="footer" /></div>', props: ['modelValue', 'title'] },
  'el-button': { template: '<button class="el-button"><slot /></button>', props: ['type', 'size', 'loading', 'link', 'icon'], emits: ['click'] },
}

export function mountPage(component: Component, extraStubs: Record<string, any> = {}) {
  return mount(component, {
    global: {
      directives: { loading: loadingDirective },
      stubs: { ...baseStubs, ...extraStubs },
    },
  })
}
