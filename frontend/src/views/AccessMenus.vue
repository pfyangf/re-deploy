<script setup>
import { computed, ref } from 'vue'
import api from '../api/client'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'

const menus = ref([])
const loading = ref(false)
const saving = ref(false)
const visible = ref(false)
const keyword = ref('')
const form = ref(emptyForm())

const groups = computed(() => menus.value.filter((menu) => menu.menuType === 'group'))
const filtered = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  if (!q) return menus.value
  return menus.value.filter((menu) => `${menu.name} ${menu.path || ''}`.toLowerCase().includes(q))
})

function emptyForm() {
  return { id: null, name: '', menuType: 'menu', parentId: null, path: '', icon: '', sortOrder: 100, visible: 1, builtin: 0 }
}

function parentName(row) {
  if (!row.parentId) return '—'
  return menus.value.find((menu) => menu.id === row.parentId)?.name || '—'
}

async function load() {
  loading.value = true
  try {
    menus.value = (await api.getMenus()) || []
  } catch (error) {
    ElMessage.error(error?.response?.data?.error || '加载菜单失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  form.value = emptyForm()
  const firstGroup = groups.value[0]
  if (firstGroup) form.value.parentId = firstGroup.id
  visible.value = true
}

function openEdit(menu) {
  form.value = {
    id: menu.id,
    name: menu.name,
    menuType: menu.menuType,
    parentId: menu.parentId,
    path: menu.path || '',
    icon: menu.icon || '',
    sortOrder: menu.sortOrder || 0,
    visible: menu.visible ? 1 : 0,
    builtin: menu.builtin,
  }
  visible.value = true
}

async function save() {
  saving.value = true
  try {
    const payload = {
      name: form.value.name,
      menuType: form.value.menuType,
      parentId: form.value.menuType === 'group' ? null : form.value.parentId,
      path: form.value.menuType === 'group' ? null : form.value.path,
      icon: form.value.icon,
      sortOrder: form.value.sortOrder,
      visible: form.value.visible,
    }
    if (form.value.id) await api.updateMenu(form.value.id, payload)
    else await api.createMenu(payload)
    ElMessage.success('已保存')
    visible.value = false
    await load()
  } catch (error) {
    ElMessage.error(error?.response?.data?.error || '保存失败')
  } finally {
    saving.value = false
  }
}

async function remove(menu) {
  try {
    await ElMessageBox.confirm(`确定删除菜单 ${menu.name}？`, '删除确认', { type: 'warning' })
    await api.deleteMenu(menu.id)
    ElMessage.success('已删除')
    await load()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error?.response?.data?.error || '删除失败')
  }
}

load()
</script>

<template>
  <div class="page-container">
    <div class="page-toolbar">
      <h2 class="page-title">菜单列表</h2>
      <el-button type="primary" :icon="Plus" @click="openCreate">新增菜单</el-button>
    </div>
    <section class="console-panel">
      <div class="console-toolbar">
        <el-input v-model="keyword" class="console-search" clearable placeholder="搜索名称或路径" style="margin-left: 0" />
        <span class="page-note">共 {{ menus.length }} 项</span>
      </div>
      <el-table v-loading="loading" :data="filtered" empty-text="还没有菜单">
        <el-table-column prop="name" label="名称" min-width="140">
          <template #default="{ row }">
            <span class="fw-medium">{{ row.name }}</span>
            <span v-if="row.builtin" class="od-tag">内置</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="90">
          <template #default="{ row }"><span class="menu-type-tag">{{ row.menuType === 'group' ? '分组' : '菜单' }}</span></template>
        </el-table-column>
        <el-table-column label="路径" min-width="160">
          <template #default="{ row }"><span class="font-mono">{{ row.path || '—' }}</span></template>
        </el-table-column>
        <el-table-column label="上级" width="120">
          <template #default="{ row }">{{ parentName(row) }}</template>
        </el-table-column>
        <el-table-column label="可见" width="90">
          <template #default="{ row }">
            <span class="state-text" :class="row.visible ? 'is-on' : 'is-off'"><i class="dot"></i>{{ row.visible ? '可见' : '隐藏' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="148" class-name="col-actions" align="right">
          <template #default="{ row }">
            <el-button text type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button text type="danger" :disabled="!!row.builtin" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="visible" width="460px">
      <template #header>
        <div>
          <h2 class="od-dialog-title">{{ form.id ? '编辑菜单' : '新增菜单' }}</h2>
          <p class="od-dialog-sub">{{ form.builtin ? '内置菜单的路径、类型与上级已锁定' : '自定义菜单登记后，可在角色里授权' }}</p>
        </div>
      </template>
      <div class="od-form">
        <label class="field"><span>名称</span><el-input v-model="form.name" maxlength="32" /></label>
        <label class="field">
          <span>类型</span>
          <el-select v-model="form.menuType" :disabled="!!form.builtin">
            <el-option label="分组" value="group" />
            <el-option label="菜单" value="menu" />
          </el-select>
        </label>
        <label v-if="form.menuType === 'menu'" class="field">
          <span>上级分组</span>
          <el-select v-model="form.parentId" :disabled="!!form.builtin">
            <el-option v-for="group in groups" :key="group.id" :label="group.name" :value="group.id" />
          </el-select>
        </label>
        <label v-if="form.menuType === 'menu'" class="field">
          <span>路径</span>
          <el-input v-model="form.path" :disabled="!!form.builtin" placeholder="/access/users" />
        </label>
        <label class="field"><span>图标</span><el-input v-model="form.icon" placeholder="Element Plus 图标名，如 User" /></label>
        <label class="field"><span>排序</span><el-input-number v-model="form.sortOrder" :min="0" :max="9999" /></label>
        <label class="field">
          <span>显示</span>
          <el-switch v-model="form.visible" :active-value="1" :inactive-value="0" />
        </label>
      </div>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 14px;
  font-size: 13px;
}
.field :deep(.el-select),
.field :deep(.el-input-number) {
  width: 100%;
}

.menu-type-tag {
  display: inline-block;
  padding: 1px 6px;
  border-radius: 3px;
  border: 1px solid var(--el-border-color);
  background: var(--el-fill-color-light);
  font-size: 10.5px;
  color: var(--el-text-color-regular);
  white-space: nowrap;
}

.state-text {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 11.5px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}

.state-text .dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--el-text-color-placeholder);
}

.state-text.is-on {
  color: var(--el-text-color-regular);
}
</style>
