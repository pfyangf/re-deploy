<script setup>
import { computed, nextTick, ref } from 'vue'
import api from '../api/client'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'

const roles = ref([])
const menus = ref([])
const loading = ref(false)
const saving = ref(false)
const visible = ref(false)
const menuVisible = ref(false)
const keyword = ref('')
const form = ref(emptyForm())
const menuRole = ref(null)
const treeRef = ref(null)

const filtered = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  if (!q) return roles.value
  return roles.value.filter((role) => `${role.name} ${role.code} ${role.description || ''}`.toLowerCase().includes(q))
})

const menuTree = computed(() => {
  const groups = menus.value.filter((menu) => menu.menuType === 'group')
  return groups.map((group) => ({
    id: group.id,
    name: group.name,
    children: menus.value.filter((menu) => menu.parentId === group.id),
  }))
})

function emptyForm() {
  return { id: null, code: '', name: '', description: '' }
}

async function load() {
  loading.value = true
  try {
    roles.value = (await api.getRoles()) || []
  } catch (error) {
    ElMessage.error(error?.response?.data?.error || '加载角色失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  form.value = emptyForm()
  visible.value = true
}

function openEdit(role) {
  form.value = { id: role.id, code: role.code, name: role.name, description: role.description || '' }
  visible.value = true
}

async function save() {
  saving.value = true
  try {
    const payload = { code: form.value.code, name: form.value.name, description: form.value.description }
    if (form.value.id) await api.updateRole(form.value.id, payload)
    else await api.createRole(payload)
    ElMessage.success('已保存')
    visible.value = false
    await load()
  } catch (error) {
    ElMessage.error(error?.response?.data?.error || '保存失败')
  } finally {
    saving.value = false
  }
}

async function openMenus(role) {
  menuRole.value = role
  menuVisible.value = true
  if (!menus.value.length) menus.value = (await api.getMenus()) || []
  await nextTick()
  const keys = role.code === 'admin'
    ? menus.value.map((menu) => menu.id)
    : (role.menuIds || [])
  treeRef.value?.setCheckedKeys(keys)
}

async function saveMenus() {
  if (!menuRole.value || menuRole.value.code === 'admin') return
  saving.value = true
  try {
    const menuIds = [
      ...(treeRef.value?.getCheckedKeys() || []),
      ...(treeRef.value?.getHalfCheckedKeys() || []),
    ]
    await api.assignRoleMenus(menuRole.value.id, menuIds)
    ElMessage.success('菜单已更新')
    menuVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(error?.response?.data?.error || '保存失败')
  } finally {
    saving.value = false
  }
}

async function remove(role) {
  try {
    await ElMessageBox.confirm(`确定删除角色 ${role.name}？`, '删除确认', { type: 'warning' })
    await api.deleteRole(role.id)
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
      <h2 class="page-title">角色列表</h2>
      <el-button type="primary" :icon="Plus" @click="openCreate">新增角色</el-button>
    </div>
    <section class="console-panel">
      <div class="console-toolbar">
        <el-input v-model="keyword" class="console-search" clearable placeholder="搜索名称、编码或说明" style="margin-left: 0" />
        <span class="page-note">共 {{ roles.length }} 个角色</span>
      </div>
      <el-table v-loading="loading" :data="filtered" empty-text="还没有角色">
        <el-table-column prop="name" label="名称" min-width="140">
          <template #default="{ row }">
            <span class="fw-medium">{{ row.name }}</span>
            <span v-if="row.builtin" class="od-tag">内置</span>
          </template>
        </el-table-column>
        <el-table-column prop="code" label="编码" min-width="120">
          <template #default="{ row }"><span class="font-mono">{{ row.code }}</span></template>
        </el-table-column>
        <el-table-column label="说明" min-width="220">
          <template #default="{ row }">
            <span>{{ row.description || '—' }}</span>
            <span class="cell-sub">已授权 {{ (row.menuIds || []).length }} 个菜单 · {{ row.userCount || 0 }} 个用户</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" class-name="col-actions" align="right">
          <template #default="{ row }">
            <el-button text type="primary" @click="openMenus(row)">菜单</el-button>
            <el-button text type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button text type="danger" :disabled="row.code === 'admin'" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="visible" width="460px">
      <template #header>
        <div>
          <h2 class="od-dialog-title">{{ form.id ? '编辑角色' : '新增角色' }}</h2>
          <p class="od-dialog-sub">{{ form.code === 'admin' ? '系统管理员默认拥有全部菜单且不可修改' : '填写角色名称、编码和说明' }}</p>
        </div>
      </template>
      <div class="od-form">
        <label class="field"><span>名称</span><el-input v-model="form.name" maxlength="32" /></label>
        <label class="field"><span>编码</span><el-input v-model="form.code" maxlength="32" :disabled="form.code === 'admin'" /></label>
        <label class="field"><span>描述</span><el-input v-model="form.description" type="textarea" maxlength="200" /></label>
      </div>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="menuVisible" width="460px">
      <template #header>
        <div>
          <h2 class="od-dialog-title">分配菜单</h2>
          <p class="od-dialog-sub">{{ menuRole?.code === 'admin' ? '系统管理员默认拥有全部菜单且不可修改' : '为角色分配可访问的菜单' }}</p>
        </div>
      </template>
      <el-tree
        class="menu-tree"
        ref="treeRef"
        node-key="id"
        show-checkbox
        default-expand-all
        :data="menuTree"
        :props="{ label: 'name', children: 'children', disabled: () => menuRole?.code === 'admin' }"
      />
      <template #footer>
        <el-button @click="menuVisible = false">关闭</el-button>
        <el-button v-if="menuRole?.code !== 'admin'" type="primary" :loading="saving" @click="saveMenus">保存</el-button>
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
.menu-tree {
  border: 1px solid var(--el-border-color);
  border-radius: 5px;
  background: var(--el-bg-color);
  max-height: 300px;
  overflow: auto;
  padding: 8px 10px;
}
</style>
