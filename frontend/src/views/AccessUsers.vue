<script setup>
import { computed, ref } from 'vue'
import api from '../api/client'
import { authState } from '../utils/session'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'

const users = ref([])
const roles = ref([])
const loading = ref(false)
const saving = ref(false)
const visible = ref(false)
const keyword = ref('')
const form = ref(emptyForm())

const filtered = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  if (!q) return users.value
  return users.value.filter((user) => `${user.username} ${user.displayName || ''} ${roleNames(user)}`.toLowerCase().includes(q))
})

function emptyForm() {
  return { id: null, username: '', displayName: '', password: '', enabled: 1, roleIds: [] }
}

function roleNames(user) {
  return (user.roles || []).map((role) => role.name).join('、') || '—'
}

function isSelf(user) {
  return authState.loginType === 'password' && user.username === authState.username
}

async function load() {
  loading.value = true
  try {
    const [userRows, roleRows] = await Promise.all([api.getUsers(), api.getRoles()])
    users.value = userRows || []
    roles.value = roleRows || []
  } catch (error) {
    ElMessage.error(error?.response?.data?.error || '加载用户失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  form.value = emptyForm()
  visible.value = true
}

function openEdit(user) {
  form.value = {
    id: user.id,
    username: user.username,
    displayName: user.displayName || '',
    password: '',
    enabled: user.enabled ? 1 : 0,
    roleIds: (user.roles || []).map((role) => role.id),
  }
  visible.value = true
}

async function save() {
  saving.value = true
  try {
    const payload = {
      displayName: form.value.displayName,
      password: form.value.password,
      enabled: form.value.enabled,
      roleIds: form.value.roleIds,
    }
    if (form.value.id) {
      await api.updateUser(form.value.id, payload)
    } else {
      await api.createUser({ ...payload, username: form.value.username })
    }
    ElMessage.success('已保存')
    visible.value = false
    await load()
  } catch (error) {
    ElMessage.error(error?.response?.data?.error || '保存失败')
  } finally {
    saving.value = false
  }
}

async function toggleEnabled(user) {
  saving.value = true
  try {
    await api.updateUser(user.id, {
      displayName: user.displayName,
      enabled: user.enabled ? 0 : 1,
      roleIds: (user.roles || []).map((role) => role.id),
    })
    ElMessage.success(user.enabled ? '已停用' : '已启用')
    await load()
  } catch (error) {
    ElMessage.error(error?.response?.data?.error || '更新状态失败')
  } finally {
    saving.value = false
  }
}

async function remove(user) {
  try {
    await ElMessageBox.confirm(`确定删除用户 ${user.username}？`, '删除确认', { type: 'warning' })
    await api.deleteUser(user.id)
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
      <h2 class="page-title">用户列表</h2>
      <el-button type="primary" :icon="Plus" @click="openCreate">新增用户</el-button>
    </div>
    <section class="console-panel">
      <div class="console-toolbar">
        <el-input v-model="keyword" class="console-search" clearable placeholder="搜索用户名、显示名或角色" style="margin-left: 0" />
        <span class="page-note">共 {{ users.length }} 个用户</span>
      </div>
      <el-table v-loading="loading" :data="filtered" empty-text="还没有用户">
        <el-table-column prop="username" label="用户名" min-width="140">
          <template #default="{ row }">
            <span class="font-mono fw-medium">{{ row.username }}</span>
            <span v-if="isSelf(row)" class="od-tag">当前账号</span>
          </template>
        </el-table-column>
        <el-table-column prop="displayName" label="显示名" min-width="140" />
        <el-table-column label="角色" min-width="180">
          <template #default="{ row }">{{ roleNames(row) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <span class="state-text" :class="row.enabled ? 'is-on' : 'is-off'"><i class="dot"></i>{{ row.enabled ? '启用' : '停用' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="188" class-name="col-actions" align="right">
          <template #default="{ row }">
            <el-button text type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button text :disabled="isSelf(row)" :title="isSelf(row) ? '不能停用当前登录账号' : ''" @click="toggleEnabled(row)">{{ row.enabled ? '停用' : '启用' }}</el-button>
            <el-button text type="danger" :disabled="isSelf(row)" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="visible" width="460px">
      <template #header>
        <div>
          <h2 class="od-dialog-title">{{ form.id ? '编辑用户' : '新增用户' }}</h2>
          <p class="od-dialog-sub">{{ form.id ? '修改账号信息与角色，用户名不可更改' : '创建可登录控制台的账号并分配角色' }}</p>
        </div>
      </template>
      <div class="od-form">
        <label class="field">
          <span>用户名</span>
          <el-input v-model="form.username" :disabled="!!form.id" maxlength="32" />
        </label>
        <label class="field">
          <span>显示名</span>
          <el-input v-model="form.displayName" maxlength="32" />
        </label>
        <label class="field">
          <span>{{ form.id ? '新密码' : '密码' }}</span>
          <el-input v-model="form.password" type="password" show-password :placeholder="form.id ? '留空则不修改' : '至少 6 位'" />
        </label>
        <label class="field">
          <span>状态</span>
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
        </label>
        <div class="field">
          <span>角色</span>
          <el-checkbox-group v-model="form.roleIds">
            <el-checkbox v-for="role in roles" :key="role.id" :value="role.id">{{ role.name }}</el-checkbox>
          </el-checkbox-group>
        </div>
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

.state-text.is-off {
  color: #8a4b45;
}
</style>
