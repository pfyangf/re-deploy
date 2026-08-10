<script setup>
import { onMounted, ref, computed } from 'vue'
import api from '../api/client'
import { ElMessage } from 'element-plus'

const history = ref([])
const loading = ref(false)

const detailVisible = ref(false)
const detailLoading = ref(false)
const detailData = ref(null)

const detailSegments = computed(() => {
  if (!detailData.value || !detailData.value.detailLogs) return []
  const segments = []
  const parts = detailData.value.detailLogs.split(/^===== \[([^\]]+)\] =====$/m)
  // parts 形如 ['', 'name host', 'log content\n', 'name2 host2', 'log2\n', ...]
  for (let i = 1; i < parts.length; i += 2) {
    const title = parts[i].trim()
    const content = (i + 1 < parts.length ? parts[i + 1] : '').trim()
    segments.push({ title, content })
  }
  return segments
})

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
  detailVisible.value = true
  detailLoading.value = true
  detailData.value = null
  try {
    const data = await api.getDeployDetail(item.id)
    detailData.value = data
  } catch (error) {
    ElMessage.error('加载详情失败')
    console.error(error)
  } finally {
    detailLoading.value = false
  }
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
        <el-table-column label="任务" min-width="160">
          <template #default="{ row }">
            <span class="fw-medium">{{ row.taskName || (row.taskId ? '任务#' + row.taskId : '-') }}</span>
          </template>
        </el-table-column>
        <el-table-column label="版本" min-width="120">
          <template #default="{ row }">
            <el-tag v-if="row.version" type="info" effect="plain" size="small">{{ row.version }}</el-tag>
            <span v-else class="text-muted">-</span>
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

    <el-dialog
      v-model="detailVisible"
      title="部署详情"
      width="80%"
      top="5vh"
      destroy-on-close
    >
      <div v-loading="detailLoading">
        <template v-if="detailData">
          <!-- 基本信息 -->
          <el-descriptions :column="3" border size="small" class="detail-desc">
            <el-descriptions-item label="ID">{{ detailData.id }}</el-descriptions-item>
            <el-descriptions-item label="任务">{{ detailData.taskName || (detailData.taskId ? '任务#' + detailData.taskId : '-') }}</el-descriptions-item>
            <el-descriptions-item label="版本">{{ detailData.version || '-' }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="getStatusType(detailData.status)" effect="light" round size="small">{{ detailData.status }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="开始">{{ detailData.startedAt ? new Date(detailData.startedAt).toLocaleString() : '-' }}</el-descriptions-item>
            <el-descriptions-item label="完成">{{ detailData.completedAt ? new Date(detailData.completedAt).toLocaleString() : '-' }}</el-descriptions-item>
            <el-descriptions-item v-if="detailData.errorMessage" label="错误" :span="3">
              <span class="text-error">{{ detailData.errorMessage }}</span>
            </el-descriptions-item>
          </el-descriptions>

          <!-- Summary 日志 -->
          <div class="section-title">概要</div>
          <pre v-if="detailData.logs" class="log-block">{{ detailData.logs }}</pre>
          <span v-else class="text-muted">（无概要日志）</span>

          <!-- 详细日志分段 -->
          <div class="section-title">详细日志</div>
          <template v-if="detailData.detailLogs">
            <el-collapse v-if="detailSegments.length">
              <el-collapse-item
                v-for="(seg, idx) in detailSegments"
                :key="idx"
                :title="seg.title"
                :name="idx"
              >
                <pre class="log-block">{{ seg.content }}</pre>
              </el-collapse-item>
            </el-collapse>
            <pre v-else class="log-block">{{ detailData.detailLogs }}</pre>
          </template>
          <span v-else class="text-muted">该记录为历史数据，无详细日志</span>
        </template>
      </div>
    </el-dialog>
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

.text-muted {
  color: var(--el-text-color-secondary);
}

.text-error {
  color: var(--el-color-danger);
}

.detail-desc {
  margin-bottom: var(--spacing-4);
}

.section-title {
  margin: var(--spacing-4) 0 var(--spacing-2);
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.log-block {
  margin: 0;
  padding: var(--spacing-3);
  background: var(--el-fill-color-light);
  border-radius: var(--el-border-radius-base);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 400px;
  overflow-y: auto;
}
</style>
