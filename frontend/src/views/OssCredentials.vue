<script setup>
import { onMounted, ref, computed } from 'vue'
import api from '../api/client'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete } from '@element-plus/icons-vue'

const credentials = ref([])
const loading = ref(false)
const keyword = ref('')
const dialogVisible = ref(false)
const saving = ref(false)
const testing = ref(false)
const form = ref(emptyForm())

const visibleCredentials = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  if (!q) return credentials.value
  return credentials.value.filter(item =>
    `${item.name} ${item.endpoint} ${item.accessKeyId}`.toLowerCase().includes(q)
  )
})

function emptyForm() {
  return { id: null, name: '', endpoint: '', accessKeyId: '', accessKeySecret: '', hasSecret: false }
}

function errorText(error, fallback) {
  return error?.response?.data?.error || fallback
}

async function loadCredentials() {
  loading.value = true
  try {
    credentials.value = await api.getOssCredentials() || []
  } catch (error) {
    ElMessage.error(errorText(error, '加载 OSS 凭证失败'))
  } finally {
    loading.value = false
  }
}

function handleAdd() {
  form.value = emptyForm()
  dialogVisible.value = true
}

function handleEdit(row) {
  form.value = {
    id: row.id,
    name: row.name,
    endpoint: row.endpoint,
    accessKeyId: row.accessKeyId,
    accessKeySecret: '',
    hasSecret: !!row.hasSecret
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!form.value.name || !form.value.endpoint || !form.value.accessKeyId) {
    ElMessage.warning('请填写名称、Endpoint 和 AccessKey ID')
    return
  }
  if (!form.value.id && !form.value.accessKeySecret) {
    ElMessage.warning('请填写 AccessKey Secret')
    return
  }
  saving.value = true
  try {
    const data = {
      name: form.value.name,
      endpoint: form.value.endpoint,
      accessKeyId: form.value.accessKeyId,
      accessKeySecret: form.value.accessKeySecret
    }
    if (form.value.id) {
      await api.updateOssCredential(form.value.id, data)
      ElMessage.success('更新成功')
    } else {
      await api.createOssCredential(data)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    await loadCredentials()
  } catch (error) {
    ElMessage.error(errorText(error, form.value.id ? '更新失败' : '创建失败'))
  } finally {
    saving.value = false
  }
}

async function handleTest() {
  if (!form.value.endpoint || !form.value.accessKeyId) {
    ElMessage.warning('请先填写 Endpoint 和 AccessKey ID')
    return
  }
  if (!form.value.id && !form.value.accessKeySecret) {
    ElMessage.warning('请填写 AccessKey Secret')
    return
  }
  testing.value = true
  try {
    const result = await api.testOssCredential({
      id: form.value.id,
      endpoint: form.value.endpoint,
      accessKeyId: form.value.accessKeyId,
      accessKeySecret: form.value.accessKeySecret
    })
    ElMessage.success(result?.message || '连接成功，凭证可用')
  } catch (error) {
    ElMessage.error(errorText(error, '验证失败'))
  } finally {
    testing.value = false
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定要删除凭证「${row.name}」吗？仍被任务引用时无法删除。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
    await api.deleteOssCredential(row.id)
    ElMessage.success('删除成功')
    await loadCredentials()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(errorText(error, '删除失败'))
    }
  }
}

onMounted(loadCredentials)
</script>

<template>
  <div class="page-container">
    <div class="page-toolbar">
      <h2 class="page-title">OSS 凭证</h2>
      <el-button type="primary" :icon="Plus" @click="handleAdd">新增凭证</el-button>
    </div>

    <section class="console-panel">
      <div class="console-toolbar">
        <el-input v-model="keyword" class="console-search" clearable placeholder="搜索名称、Endpoint 或 AccessKey ID" style="margin-left: 0" />
        <span class="page-note">共 {{ credentials.length }} 条凭证</span>
      </div>
      <el-table v-loading="loading" :data="visibleCredentials" empty-text="还没有 OSS 凭证">
        <el-table-column label="名称" min-width="160">
          <template #default="{ row }">
            <span class="fw-medium">{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="endpoint" label="Endpoint" min-width="240">
          <template #default="{ row }">
            <span class="font-mono">{{ row.endpoint }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="accessKeyId" label="AccessKey ID" min-width="180">
          <template #default="{ row }">
            <span class="font-mono">{{ row.accessKeyId }}</span>
          </template>
        </el-table-column>
        <el-table-column label="Secret" width="100">
          <template #default="{ row }">
            {{ row.hasSecret ? '已保存' : '未设置' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="168" class-name="col-actions" align="right">
          <template #default="{ row }">
            <el-button text type="primary" :icon="Edit" @click="handleEdit(row)">编辑</el-button>
            <el-button text type="danger" :icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <footer class="panel-foot">任务选择一条凭证，再填写 Bucket 和固定对象路径</footer>
    </section>

    <el-dialog v-model="dialogVisible" width="520" :close-on-click-modal="false">
      <template #header>
        <div>
          <h2 class="od-dialog-title">{{ form.id ? '编辑 OSS 凭证' : '新增 OSS 凭证' }}</h2>
          <p class="od-dialog-sub">同一地域共用一套 AccessKey。Secret 只保存在服务端</p>
        </div>
      </template>
      <el-form class="od-form" :model="form" label-position="top">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" maxlength="100" placeholder="例如 杭州生产" />
        </el-form-item>
        <el-form-item label="Endpoint" required>
          <el-input v-model="form.endpoint" class="font-mono" placeholder="https://oss-cn-hangzhou.aliyuncs.com" />
        </el-form-item>
        <el-form-item label="AccessKey ID" required>
          <el-input v-model="form.accessKeyId" class="font-mono" maxlength="128" placeholder="LTAI..." />
        </el-form-item>
        <el-form-item label="AccessKey Secret" :required="!form.id">
          <el-input
            v-model="form.accessKeySecret"
            type="password"
            show-password
            maxlength="256"
            :placeholder="form.hasSecret ? '已保存，留空则不修改' : '请输入 AccessKey Secret'"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="od-dialog-note">Secret 加密后保存，接口不再回传</span>
        <div class="od-dialog-actions">
          <el-button :loading="testing" @click="handleTest">验证</el-button>
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="handleSubmit">保存</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>
