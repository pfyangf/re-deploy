<script setup>
import { onMounted, onUnmounted, ref, computed } from 'vue'
import api from '../api/client'

const stats = ref({
  totalServers: 0,
  onlineServers: 0,
  totalDeploys: 0,
  successRate: 0
})

const recentDeploys = ref([])
const activeDeploys = ref([])
const servers = ref([])
const historyAll = ref([])
const loading = ref(true)
const syncedAt = ref('')
let refreshTimer = null

const rateClass = computed(() => {
  if (!stats.value.totalDeploys) return ''
  if (stats.value.successRate >= 90) return 'stat-value-ok'
  if (stats.value.successRate >= 70) return 'stat-value-queue'
  return 'stat-value-fail'
})

const onlineSub = computed(() => {
  if (!stats.value.totalServers) return '尚未登记目标机'
  const rate = ((stats.value.onlineServers / stats.value.totalServers) * 100).toFixed(1)
  const offline = stats.value.totalServers - stats.value.onlineServers
  if (!offline) return `在线率 ${rate}% · 全部心跳正常`
  return `在线率 ${rate}% · ${offline} 台心跳中断`
})

const serverSub = computed(() => {
  if (!stats.value.totalServers) return '尚未登记目标机'
  const groups = new Set(servers.value.map(s => s.groupId).filter(id => id != null))
  const offline = stats.value.totalServers - stats.value.onlineServers
  return `${groups.size} 个分组 · ${offline} 台离线`
})

const failedCount = computed(() => historyAll.value.filter(h => h.status === 'failed').length)
const todayCount = computed(() => {
  const start = new Date()
  start.setHours(0, 0, 0, 0)
  return historyAll.value.filter(h => new Date(h.startedAt || h.createdAt) >= start).length
})
const running = computed(() => activeDeploys.value.filter(d => d.status === 'running'))
const queued = computed(() => activeDeploys.value.filter(d => d.status === 'pending'))

async function loadData(silent = false) {
  if (!silent) loading.value = true
  try {
    const [serversRes, historyRes] = await Promise.all([
      api.getServers(),
      api.getDeployHistory()
    ])

    if (serversRes) {
      servers.value = serversRes
      stats.value.totalServers = serversRes.length
      stats.value.onlineServers = serversRes.filter(s => s.status === 'online').length
    }

    if (historyRes) {
      const successCount = historyRes.filter(h => h.status === 'success').length
      stats.value.totalDeploys = historyRes.length
      stats.value.successRate = historyRes.length > 0
        ? Math.round((successCount / historyRes.length) * 100)
        : 0
      historyAll.value = historyRes
      recentDeploys.value = historyRes.slice(0, 8)
      activeDeploys.value = historyRes
        .filter(h => h.status === 'running' || h.status === 'pending')
        .sort((a, b) => a.id - b.id)
      syncedAt.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
    }
  } catch (error) {
    console.error('Failed to load dashboard data:', error)
  } finally {
    if (!silent) loading.value = false
  }
}

function statusClass(status) {
  const map = { success: 'rd-ok', failed: 'rd-fail', running: 'rd-run', pending: 'rd-queue', cancelled: 'rd-off' }
  return `rd-status ${map[status] || 'rd-off'}`
}

function targetLabel(row) {
  return row.taskName || (row.taskId ? `任务#${row.taskId}` : `部署#${row.id}`)
}

function durationText(row) {
  if (!row.startedAt || !row.completedAt) return '—'
  const ms = new Date(row.completedAt) - new Date(row.startedAt)
  if (!Number.isFinite(ms) || ms < 0) return '—'
  if (ms < 1000) return `${ms}ms`
  const sec = Math.round(ms / 1000)
  if (sec < 60) return `${sec}s`
  return `${Math.floor(sec / 60)}m ${sec % 60}s`
}

function clockText(row) {
  const value = row.startedAt || row.createdAt
  if (!value) return '—'
  return new Date(value).toLocaleTimeString('zh-CN', { hour12: false, hour: '2-digit', minute: '2-digit' })
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

onMounted(() => {
  loadData()
  // 队列状态会随部署完成/排队转执行而变化，静默轮询避免遮罩闪烁
  refreshTimer = setInterval(() => loadData(true), 5000)
})

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
})
</script>

<template>
  <div v-loading="loading">
    <div class="page-toolbar">
      <h2 class="page-title">集群概览</h2>
      <span class="page-note">{{ syncedAt ? `数据更新于 ${syncedAt}` : '正在读取集群状态' }}</span>
    </div>
    <div class="stats-grid">
      <div class="stat-card-plain">
        <div class="stat-label">服务器总数</div>
        <div class="stat-value"><b>{{ stats.totalServers }}</b><span>台</span></div>
        <div class="stat-sub">{{ serverSub }}</div>
      </div>
      <div class="stat-card-plain">
        <div class="stat-label">在线服务器</div>
        <div class="stat-value" :class="{ 'stat-value-ok': stats.onlineServers > 0 }"><b>{{ stats.onlineServers }}</b><span>台</span></div>
        <div class="stat-sub">{{ onlineSub }}</div>
      </div>
      <div class="stat-card-plain">
        <div class="stat-label">部署总数</div>
        <div class="stat-value"><b>{{ stats.totalDeploys }}</b><span>次</span></div>
        <div class="stat-sub">今日 {{ todayCount }} 次</div>
      </div>
      <div class="stat-card-plain">
        <div class="stat-label">成功率</div>
        <div class="stat-value" :class="rateClass"><b>{{ stats.successRate }}</b><span>%</span></div>
        <div class="stat-sub">累计失败 {{ failedCount }} 次</div>
      </div>
    </div>

    <div class="dash-grid">
      <section class="console-panel">
        <header class="panel-head">
          <h3>部署队列</h3>
          <span>{{ running.length }} 进行中 · {{ queued.length }} 排队中</span>
        </header>
        <div v-if="!running.length && !queued.length" class="empty-block">
          <strong>队列为空</strong>
          <p>当前没有进行中的部署任务。</p>
        </div>
        <div v-else class="queue-list">
          <div v-for="row in running" :key="`run-${row.id}`" class="queue-row">
            <div>
              <div class="fw-medium">{{ targetLabel(row) }}</div>
              <div class="cell-sub">{{ row.version || '未填写版本' }}</div>
            </div>
            <span :class="statusClass(row.status)">{{ getStatusText(row.status) }}</span>
          </div>
          <div v-if="queued.length" class="queue-divider">排队中</div>
          <div v-for="(row, index) in queued" :key="`queue-${row.id}`" class="queue-row">
            <div style="display:flex;gap:8px;align-items:center">
              <span class="q-pos">{{ index + 1 }}</span>
              <div>
                <div class="fw-medium">{{ targetLabel(row) }}</div>
                <div class="cell-sub">{{ row.version || '未填写版本' }}</div>
              </div>
            </div>
            <span :class="statusClass(row.status)">{{ getStatusText(row.status) }}</span>
          </div>
        </div>
        <footer class="panel-foot">
          <span>按提交顺序执行</span>
          <router-link to="/deploy">发起部署</router-link>
        </footer>
      </section>

      <section class="console-panel">
        <header class="panel-head">
          <h3>最近部署</h3>
          <router-link to="/history">查看全部</router-link>
        </header>
        <el-table :data="recentDeploys" empty-text="暂无部署记录" @row-click="(row) => $router.push('/history')">
          <el-table-column label="时间" width="72">
            <template #default="{ row }"><span class="font-mono">{{ clockText(row) }}</span></template>
          </el-table-column>
          <el-table-column label="目标" min-width="120">
            <template #default="{ row }">{{ targetLabel(row) }}</template>
          </el-table-column>
          <el-table-column label="来源" min-width="100">
            <template #default="{ row }"><span class="font-mono">{{ row.version || '—' }}</span></template>
          </el-table-column>
          <el-table-column label="结果" width="88">
            <template #default="{ row }"><span :class="statusClass(row.status)">{{ getStatusText(row.status) }}</span></template>
          </el-table-column>
          <el-table-column label="耗时" width="80">
            <template #default="{ row }"><span class="font-mono">{{ durationText(row) }}</span></template>
          </el-table-column>
        </el-table>
      </section>
    </div>
  </div>
</template>

<style scoped>
.stat-label { font-size: 11.5px; color: var(--el-text-color-secondary); }
.stat-value { display: flex; align-items: baseline; gap: 5px; margin-top: 6px; }
.stat-value b { font-size: 27px; line-height: 1.1; font-weight: 600; font-variant-numeric: tabular-nums; }
.stat-value span { font-size: 11.5px; color: var(--el-text-color-secondary); font-weight: 400; }
.stat-sub { margin-top: 5px; font-size: 11.5px; color: var(--el-text-color-secondary); }
.stat-value-ok b { color: #0e7250; }
.stat-value-queue b { color: #8a5a06; }
.stat-value-fail b { color: #ae2a1f; }
html.dark .stat-value-ok b { color: #7dcea0; }
html.dark .stat-value-queue b { color: #e0b15a; }
html.dark .stat-value-fail b { color: #f08178; }
</style>
