<script setup>
import { onMounted, ref, computed } from 'vue'
import api from '../api/client'
import { ElMessage } from 'element-plus'

const history = ref([])
const loading = ref(false)
const detailLoading = ref(false)
const detailData = ref(null)

const statusFilter = ref('all')
const keyword = ref('')

const statusOptions = [
  { id: 'all', label: '全部' },
  { id: 'success', label: '成功' },
  { id: 'failed', label: '失败' },
  { id: 'running', label: '执行中' },
  { id: 'pending', label: '排队中' },
  { id: 'cancelled', label: '已取消' }
]

const statusCounts = computed(() => {
  const counts = { all: history.value.length, success: 0, failed: 0, running: 0, pending: 0, cancelled: 0 }
  history.value.forEach(item => {
    if (counts[item.status] != null) counts[item.status] += 1
  })
  return counts
})

const filteredHistory = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  return history.value.filter(item => {
    if (statusFilter.value !== 'all' && item.status !== statusFilter.value) return false
    if (!q) return true
    const text = `${item.id} ${item.taskName || ''} ${item.version || ''} ${item.taskId || ''}`.toLowerCase()
    return text.includes(q)
  })
})

function statusClass(status) {
  const map = {
    success: 'rd-status rd-ok',
    failed: 'rd-status rd-fail',
    running: 'rd-status rd-run',
    pending: 'rd-status rd-queue',
    cancelled: 'rd-status rd-off'
  }
  return map[status] || 'rd-status rd-off'
}

const detailTargets = computed(() => {
  const list = detailData.value && detailData.value.targets
  return Array.isArray(list) ? list : []
})

const targetSummary = computed(() => {
  const n = detailTargets.value.length
  return n ? `${n} 台服务器` : '—'
})

function showServerGroup(server) {
  if (!server.groupName || !detailData.value) return false
  return detailData.value.groupName === '全部分组' || detailData.value.groupName !== server.groupName
}

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

function getStatusText(status) {
  const map = {
    success: '成功',
    failed: '失败',
    running: '执行中',
    pending: '排队中',
    cancelled: '已取消'
  }
  return map[status] || status
}

function durationText(row) {
  if (!row.startedAt || !row.completedAt) return '—'
  const ms = new Date(row.completedAt) - new Date(row.startedAt)
  if (!Number.isFinite(ms) || ms < 0) return '—'
  const sec = Math.round(ms / 1000)
  if (sec < 60) return `${sec}s`
  return `${Math.floor(sec / 60)}m ${sec % 60}s`
}

async function viewDetail(item) {
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
      <h2 class="page-title">部署记录</h2>
      <div class="toolbar-actions">
        <span class="page-note">共 {{ history.length }} 条</span>
        <el-button @click="loadHistory">刷新</el-button>
      </div>
    </div>

    <div class="history-grid">
      <section class="step-panel">
        <div class="history-toolbar">
          <div class="chips">
            <button
              v-for="item in statusOptions"
              :key="item.id"
              type="button"
              class="rd-chip"
              :class="{ 'is-on': statusFilter === item.id }"
              @click="statusFilter = item.id"
            >
              {{ item.label }}
              <span class="rd-chip-count">{{ statusCounts[item.id] || 0 }}</span>
            </button>
          </div>
          <el-input
            v-model="keyword"
            class="history-search"
            clearable
            placeholder="搜索任务、版本或 ID"
          />
        </div>
        <el-table
          v-loading="loading"
          :data="filteredHistory"
          empty-text="没有匹配的部署记录"
          highlight-current-row
          @row-click="viewDetail"
        >
          <el-table-column label="开始时间" min-width="150">
            <template #default="{ row }">
              <span class="font-mono">{{ row.startedAt ? new Date(row.startedAt).toLocaleString() : '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="任务" min-width="140">
            <template #default="{ row }">
              {{ row.taskName || (row.taskId ? '任务#' + row.taskId : '-') }}
            </template>
          </el-table-column>
          <el-table-column label="版本" min-width="110">
            <template #default="{ row }">
              <span class="font-mono">{{ row.version || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="结果" width="96">
            <template #default="{ row }">
              <span :class="statusClass(row.status)">{{ getStatusText(row.status) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="耗时" width="80">
            <template #default="{ row }">
              <span class="font-mono">{{ durationText(row) }}</span>
            </template>
          </el-table-column>
        </el-table>
        <footer class="panel-foot">共 {{ filteredHistory.length }} 条记录</footer>
      </section>

      <aside class="step-panel history-detail" v-loading="detailLoading">
        <header class="step-head">
          <h3>日志详情</h3>
          <span v-if="detailData" :class="statusClass(detailData.status)">{{ getStatusText(detailData.status) }}</span>
        </header>
        <div v-if="detailData" class="step-body">
          <dl class="kv">
            <div><dt>分组</dt><dd>{{ detailData.groupName || '—' }}</dd></div>
            <div><dt>任务</dt><dd>{{ detailData.taskName || (detailData.taskId ? '任务#' + detailData.taskId : '—') }}</dd></div>
            <div><dt>目标</dt><dd>{{ targetSummary }}</dd></div>
            <div><dt>版本</dt><dd class="font-mono">{{ detailData.version || '—' }}</dd></div>
            <div v-if="detailData.jenkinsBuildNumber"><dt>构建号</dt><dd class="font-mono">#{{ String(detailData.jenkinsBuildNumber).replace(/^#/, '') }}</dd></div>
            <div><dt>开始</dt><dd>{{ detailData.startedAt ? new Date(detailData.startedAt).toLocaleString() : '—' }}</dd></div>
            <div><dt>完成</dt><dd>{{ detailData.completedAt ? new Date(detailData.completedAt).toLocaleString() : '—' }}</dd></div>
          </dl>
          <h4>服务器</h4>
          <ul v-if="detailTargets.length" class="target-list">
            <li v-for="server in detailTargets" :key="server.id || server.name">
              <span>{{ server.name || ('服务器#' + server.id) }}</span>
              <span class="font-mono target-meta">
                <template v-if="server.host">{{ server.host }}<template v-if="server.port">:{{ server.port }}</template></template>
                <template v-if="showServerGroup(server)"> · {{ server.groupName }}</template>
              </span>
            </li>
          </ul>
          <p v-else class="empty-note">没有记录目标服务器</p>
          <p v-if="detailData.errorMessage" class="error-line">{{ detailData.errorMessage }}</p>
          <h4>概要</h4>
          <pre v-if="detailData.logs" class="log-block">{{ detailData.logs }}</pre>
          <p v-else class="empty-note">无概要日志</p>
          <h4>详细日志</h4>
          <template v-if="detailData.detailLogs">
            <div v-if="detailSegments.length" class="log-segments">
              <section v-for="(seg, idx) in detailSegments" :key="idx">
                <div class="seg-title">{{ seg.title }}</div>
                <pre class="log-block">{{ seg.content }}</pre>
              </section>
            </div>
            <pre v-else class="log-block">{{ detailData.detailLogs }}</pre>
          </template>
          <p v-else class="empty-note">该记录没有分段日志</p>
        </div>
        <p v-else class="empty-detail">点击左侧一条记录，在这里查看分段日志。</p>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.history-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(280px, 0.75fr);
  gap: 12px;
  align-items: start;
}

.step-panel {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  min-width: 0;
}

.history-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 10px 12px;
  border-bottom: 1px solid var(--el-border-color);
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.history-search {
  width: 200px;
  margin-left: auto;
}

.step-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 11px 16px;
  border-bottom: 1px solid var(--el-border-color);
}

.step-head h3,
.step-body h4 {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
}

.step-body {
  padding: 14px 16px;
}

.step-body h4 {
  margin: 14px 0 8px;
}

.kv div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 6px 0;
  border-bottom: 1px solid var(--el-border-color);
  font-size: 12.5px;
}

.kv dt {
  color: var(--el-text-color-secondary);
}

.kv dd {
  margin: 0;
  text-align: right;
}

.target-list {
  margin: 0;
  padding: 0;
  list-style: none;
  max-height: 160px;
  overflow: auto;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
}

.target-list li {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  padding: 6px 8px;
  font-size: 12px;
  border-bottom: 1px solid var(--el-border-color);
}

.target-list li:last-child {
  border-bottom: 0;
}

.target-meta {
  color: var(--el-text-color-secondary);
  text-align: right;
}

.error-line,
.empty-note,
.empty-detail {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.error-line {
  color: #ae2a1f;
}

.empty-detail {
  padding: 28px 16px;
}

.seg-title {
  margin-bottom: 6px;
  font-size: 12px;
  font-weight: 600;
}

.log-block {
  margin: 0 0 10px;
  padding: 10px 12px;
  background: var(--rd-surface-console);
  color: var(--rd-surface-console-ink);
  border: 1px solid var(--rd-border-console);
  border-radius: 4px;
  font-family: var(--el-font-family-mono);
  font-size: 12px;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 240px;
  overflow: auto;
}

@media (max-width: 980px) {
  .history-grid {
    grid-template-columns: 1fr;
  }

  .history-search {
    margin-left: 0;
    width: 100%;
  }
}
</style>
