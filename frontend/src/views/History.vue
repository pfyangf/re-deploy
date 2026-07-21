<script setup>
import { onMounted, ref } from 'vue'
import api from '../api/client'
import { ElMessage } from 'element-plus'

const history = ref([])
const loading = ref(false)

async function loadHistory() {
  loading.value = true
  try {
    const data = await api.getDeployHistory()
    history.value = data || []
  } catch (error) {
    ElMessage.error('加载历史失败')
    console.error(error)
  } finally {
    loading.value = false
  }
}

function getStatusType(status) {
  const map = { success: 'success', failed: 'danger', running: 'warning' }
  return map[status] || 'info'
}

async function viewDetail(item) {
  ElMessage.info(`部署详情 ID: ${item.id}（功能开发中）`)
}

onMounted(() => {
  loadHistory()
})
</script>

<template>
  <div class="page-container">
    <div class="page-toolbar">
      <h2 class="page-title">部署历史</h2>
      <el-button @click="loadHistory">刷新</el-button>
    </div>

    <el-card shadow="never" class="table-card">
      <el-table
        v-loading="loading"
        :data="history"
        stripe
        empty-text="暂无部署记录"
      >
        <el-table-column prop="id" label="ID" width="70">
          <template #default="{ row }">
            <span class="font-mono">{{ row.id }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="taskId" label="任务ID" width="100">
          <template #default="{ row }">
            {{ row.taskId || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="版本" min-width="120">
          <template #default="{ row }">
            <span class="fw-medium">{{ row.version || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" effect="light" round>
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="开始时间" min-width="160">
          <template #default="{ row }">
            <span class="text-sm">{{ row.startedAt ? new Date(row.startedAt).toLocaleString() : '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="完成时间" min-width="160">
          <template #default="{ row }">
            <span class="text-sm">{{ row.completedAt ? new Date(row.completedAt).toLocaleString() : '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click="viewDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.page-container {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-4);
}

.page-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.page-title {
  margin: 0;
  font-size: 1.5rem;
  font-weight: 600;
  color: var(--el-text-color-primary);
  letter-spacing: -0.02em;
}

.table-card {
  border-radius: var(--el-border-radius-round);
}

.fw-medium {
  font-weight: 500;
}

.text-sm {
  font-size: var(--text-sm);
}
</style>
