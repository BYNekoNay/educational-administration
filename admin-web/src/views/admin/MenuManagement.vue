<template>
  <div class="menu-page">
    <div style="margin-bottom: 12px; display: flex; justify-content: space-between; align-items: center">
      <span style="color: #606266; font-size: 14px">菜单树管理（调整层级、排序、图标、路由）</span>
      <el-button type="primary" @click="showDialog()" :icon="Plus">新增顶级菜单</el-button>
    </div>

    <el-table
      :data="menuTree"
      row-key="id"
      border
      stripe
      v-loading="loading"
      size="small"
      default-expand-all
      :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
    >
      <el-table-column prop="menuName" label="菜单名称" min-width="140" />
      <el-table-column prop="icon" label="图标" width="100" align="center">
        <template #default="{ row }">
          <el-icon v-if="row.icon && knownIcons.has(row.icon)" style="font-size: 18px">
            <component :is="row.icon" />
          </el-icon>
          <span v-else-if="row.icon" style="color: #e6a23c" :title="`未注册的图标名: ${row.icon}`">⚠</span>
          <span v-else style="color: #c0c4cc">—</span>
        </template>
      </el-table-column>
      <el-table-column prop="path" label="路由路径" min-width="160">
        <template #default="{ row }">
          <span v-if="row.path">{{ row.path }}</span>
          <span v-else style="color: #c0c4cc">(分组)</span>
        </template>
      </el-table-column>
      <el-table-column prop="permissionCode" label="权限码" width="140">
        <template #default="{ row }">
          {{ row.permissionCode || '—' }}
        </template>
      </el-table-column>
      <el-table-column prop="sortOrder" label="排序" width="70" align="center" />
      <el-table-column prop="visible" label="可见" width="70" align="center">
        <template #default="{ row }">
          <el-tag :type="row.visible === 1 ? 'success' : 'info'" size="small">
            {{ row.visible === 1 ? '显示' : '隐藏' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="240" align="center" fixed="right">
        <template #default="{ row }">
          <div style="display: flex; gap: 4px; white-space: nowrap; align-items: center">
            <el-button link type="primary" size="small" @click="showDialog(row)">
              新增子菜单
            </el-button>
            <el-button link type="primary" size="small" @click="showDialog(row, true)">编辑</el-button>
            <el-popconfirm title="确定删除该菜单？所有子菜单也将被删除" @confirm="handleDelete(row.id)">
              <template #reference>
                <el-button link type="danger" size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <!-- 菜单表单弹窗 -->
    <el-dialog
      :title="editing?.id ? '编辑菜单' : parentForChild ? `新增「${parentForChild.menuName}」子菜单` : '新增顶级菜单'"
      v-model="dialogVisible"
      width="460px"
      @closed="onDialogClosed"
    >
      <el-form :model="form" label-width="100px">
        <el-form-item label="菜单名称" required>
          <el-input v-model="form.menuName" placeholder="如 角色管理" />
        </el-form-item>
        <el-form-item label="图标名称">
          <el-input v-model="form.icon" placeholder="如 Monitor, Setting, Document, Money" />
        </el-form-item>
        <el-form-item label="路由路径">
          <el-input v-model="form.path" placeholder="如 /admin/roles（分组留空）" />
        </el-form-item>
        <el-form-item label="权限码">
          <el-input v-model="form.permissionCode" placeholder="如 menu:role" />
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number v-model="form.sortOrder" :min="0" :max="99" controls-position="right" />
        </el-form-item>
        <el-form-item label="可见状态">
          <el-switch v-model="form.visibleBool" active-text="可见" inactive-text="隐藏" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">
          {{ editing?.id ? '保存修改' : '确认新增' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { showError } from '@/utils/error'
import { Plus } from '@element-plus/icons-vue'
import { menuApi } from '@/api/auth'

const menuTree = ref<any[]>([])
const loading = ref(false)

/** 已注册图标白名单——须与 AppLayout.vue 侧栏 iconMap 保持同步，
    未注册的图标名直接 <component :is> 会触发 Vue 告警且侧栏静默无图标 */
const knownIcons = new Set(['Monitor', 'Setting', 'Document', 'Money'])
const dialogVisible = ref(false)
const saving = ref(false)
const editing = ref<any>(null)
const parentForChild = ref<any>(null)

function defaultForm() {
  return reactive({
    menuName: '',
    icon: '',
    path: '',
    permissionCode: '',
    sortOrder: 0,
    visibleBool: true,
  })
}
const form = defaultForm()

/** 弹窗关闭后复位编辑状态与表单（避免在模板中对 const 绑定直接赋值） */
function onDialogClosed() {
  editing.value = null
  parentForChild.value = null
  Object.assign(form, defaultForm())
}

async function loadTree() {
  loading.value = true
  try {
    const res = await menuApi.tree()
    menuTree.value = res.data || []
  } catch (e) { showError(e, '加载菜单树失败') }
  finally { loading.value = false }
}

function showDialog(row?: any, isEdit?: boolean) {
  editing.value = null
  parentForChild.value = null

  if (row && isEdit) {
    // 编辑模式
    editing.value = row
    form.menuName = row.menuName
    form.icon = row.icon || ''
    form.path = row.path || ''
    form.permissionCode = row.permissionCode || ''
    form.sortOrder = row.sortOrder ?? 0
    form.visibleBool = row.visible === 1
  } else if (row && !isEdit) {
    // 新增子菜单模式
    parentForChild.value = row
    Object.assign(form, {
      menuName: '',
      icon: '',
      path: '',
      permissionCode: '',
      sortOrder: 0,
      visibleBool: true,
    })
  } else {
    // 新增顶级菜单
    Object.assign(form, {
      menuName: '',
      icon: '',
      path: '',
      permissionCode: '',
      sortOrder: 0,
      visibleBool: true,
    })
  }
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.menuName) {
    ElMessage.warning('菜单名称不能为空')
    return
  }
  saving.value = true
  try {
    const payload: any = {
      menuName: form.menuName,
      icon: form.icon || null,
      path: form.path || null,
      permissionCode: form.permissionCode || null,
      sortOrder: form.sortOrder,
      visible: form.visibleBool ? 1 : 0,
    }

    if (editing.value?.id) {
      await menuApi.update(editing.value.id, payload)
      ElMessage.success('菜单已更新')
    } else {
      payload.parentId = parentForChild.value?.id || 0
      await menuApi.create(payload)
      ElMessage.success('菜单已创建')
    }
    dialogVisible.value = false
    await loadTree()
  } catch (e: any) {
    showError(e, '操作失败')
  } finally { saving.value = false }
}

async function handleDelete(id: number) {
  try {
    await menuApi.delete(id)
    ElMessage.success('菜单已删除（含子节点）')
    await loadTree()
  } catch (e: any) {
    showError(e, '删除失败')
  }
}

onMounted(() => loadTree())
</script>
