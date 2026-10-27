<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import api from '../api/client'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Download, Delete, Refresh, Search, CircleClose, Document } from '@element-plus/icons-vue'

const servers = ref([])
const groups = ref([])
const sessions = ref([])
const total = ref(0)
const loading = ref(false)

const filter = ref({ serverId: null, status: null })
const page = ref({ limit: 20, offset: 0 })

const triggerDialogVisible = ref(false)
const triggerForm = ref({ groupId: null, serverId: null, remotePath: '' })
const triggering = ref(false)

let pollTimer = null

const serverNameMap = computed(() => {
  const m = {}
  for (const s of servers.value) m[s.id] = s.name
  return m
})

// 默认分组（DataMigration 会把未分组的服务器归入 default 组）
const defaultGroupId = computed(() => {
  const d = groups.value.find(g => g.name === 'default')
  return d ? d.id : null
})

// 触发对话框中、按所选分组过滤后的服务器列表
const triggerServerOptions = computed(() => {
  const gid = triggerForm.value.groupId
  if (gid === null || gid === undefined) return servers.value
  return servers.value.filter(s => {
    if (s.groupId === gid) return true
    // 兼容尚未分配分组（groupId 为空）的服务器，归入默认分组
    return (s.groupId === null || s.groupId === undefined) && gid === defaultGroupId.value
  })
})

const statusOptions = [
  { value: 'DOWNLOADING', label: '下载中', type: 'warning' },
  { value: 'SUCCESS', label: '成功', type: 'success' },
  { value: 'FAILED', label: '失败', type: 'danger' },
  { value: 'CANCELLED', label: '已取消', type: 'info' }
]

function downloadStatusClass(status) {
  if (status === 'SUCCESS') return 'rd-status rd-ok'
  if (status === 'FAILED') return 'rd-status rd-fail'
  if (status === 'DOWNLOADING') return 'rd-status rd-run'
  if (status === 'PENDING') return 'rd-status rd-queue'
  return 'rd-status rd-off'
}

function baseName(p) {
  if (!p) return ''
  const parts = String(p).split(/[\\/]/)
  return parts[parts.length - 1] || ''
}

function formatBytes(n) {
  if (n == null) return '-'
  if (n < 1024) return n + ' B'
  if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB'
  if (n < 1024 * 1024 * 1024) return (n / 1024 / 1024).toFixed(1) + ' MB'
  return (n / 1024 / 1024 / 1024).toFixed(2) + ' GB'
}

function progressPct(row) {
  if (!row.fileSize || row.fileSize === 0) return row.status === 'SUCCESS' ? 100 : 0
  return Math.min(100, Math.round((row.bytesReceived || 0) * 100 / row.fileSize))
}

async function loadServers() {
  try {
    const [svrs, grps] = await Promise.all([
      api.getServers(),
      api.getGroups().catch(() => [])
    ])
    servers.value = svrs || []
    groups.value = grps || []
  } catch (error) {
    console.error(error)
  }
}

async function loadSessions() {
  loading.value = true
  try {
    const params = {
      ...filter.value,
      limit: page.value.limit,
      offset: page.value.offset
    }
    Object.keys(params).forEach(k => (params[k] === null || params[k] === '') && delete params[k])
    const resp = await api.listRemoteDownloads(params)
    sessions.value = resp.rows || []
    total.value = resp.total || 0
  } catch (error) {
    console.error(error)
    ElMessage.error('加载下载历史失败')
  } finally {
    loading.value = false
  }
}

async function loadAll() {
  await Promise.all([loadServers(), loadSessions()])
}

function onFilterChange() {
  page.value.offset = 0
  loadSessions()
}

function onPageChange(p) {
  page.value.limit = p.pageSize
  page.value.offset = (p.pageNum - 1) * p.pageSize
  loadSessions()
}

function openTrigger() {
  triggerForm.value = { groupId: null, serverId: null, remotePath: '' }
  triggerDialogVisible.value = true
}

// 切换分组后，把已选服务器重置为该分组下的第一台（避免选中被过滤掉的服务器）
function onTriggerGroupChange() {
  triggerForm.value.serverId = triggerServerOptions.value[0]?.id || null
}

async function handleTrigger() {
  if (!triggerForm.value.serverId) {
    ElMessage.warning('请选择服务器')
    return
  }
  if (!triggerForm.value.remotePath?.trim()) {
    ElMessage.warning('请输入远端路径')
    return
  }
  if (!triggerForm.value.remotePath.startsWith('/')) {
    ElMessage.warning('路径必须为绝对路径（以 / 开头）')
    return
  }
  triggering.value = true
  try {
    const resp = await api.triggerRemoteDownload(triggerForm.value.serverId, triggerForm.value.remotePath.trim())
    ElMessage.success(`已启动下载（session #${resp.sessionId}），可在下方表格查看进度`)
    triggerDialogVisible.value = false
    await loadSessions()
    startPolling()
  } catch (error) {
    ElMessage.error(error.response?.data?.error || '启动下载失败')
  } finally {
    triggering.value = false
  }
}

function startPolling() {
  if (pollTimer) return
  pollTimer = setInterval(async () => {
    // 只轮询有进行中的记录
    const hasActive = sessions.value.some(s => s.status === 'DOWNLOADING' || s.status === 'PENDING')
    if (hasActive) {
      await loadSessions()
    } else {
      stopPolling()
    }
  }, 3000)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

async function handleDownload(row) {
  const url = api.getRemoteDownloadFileUrl(row.id)
  // 用隐藏的 a 标签下载
  const token = localStorage.getItem('adminToken') || ''
  // fetch 拿 blob 加 header，再触发下载
  try {
    const resp = await fetch(url, { headers: { Authorization: `Bearer ${token}` } })
    if (!resp.ok) {
      const errBody = await resp.json().catch(() => ({ error: resp.statusText }))
      ElMessage.error('下载失败: ' + (errBody.error || resp.statusText))
      return
    }
    const blob = await resp.blob()
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = baseName(row.remotePath) || baseName(row.localPath) || `download-${row.id}.bin`
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(a.href)
  } catch (e) {
    ElMessage.error('下载失败: ' + e.message)
  }
}

async function handleCancel(row) {
  try {
    await ElMessageBox.confirm('确定取消此下载任务？', '取消确认',
      { type: 'warning', confirmButtonText: '取消下载', cancelButtonText: '返回' })
    await api.cancelRemoteDownload(row.id)
    ElMessage.success('已取消')
    await loadSessions()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('取消失败')
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除下载记录 #${row.id}？同时会从磁盘删除文件。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '返回' })
    await api.deleteRemoteDownload(row.id)
    ElMessage.success('已删除')
    await loadSessions()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

onMounted(() => {
  loadAll()
  startPolling()
})
onUnmounted(() => {
  stopPolling()
})
</script>

<template>
  <div class="page-container">
    <div class="page-toolbar">
      <h2 class="page-title">远端下载</h2>
      <div class="toolbar-actions">
        <el-button :icon="Refresh" @click="loadSessions">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openTrigger">触发下载</el-button>
      </div>
    </div>

    <section class="console-panel">
      <div class="console-toolbar">
        <div class="chips">
          <button type="button" class="rd-chip" :class="{ 'is-on': !filter.status }" @click="filter.status = null; onFilterChange()">全部</button>
          <button
            v-for="item in statusOptions"
            :key="item.value"
            type="button"
            class="rd-chip"
            :class="{ 'is-on': filter.status === item.value }"
            @click="filter.status = item.value; onFilterChange()"
          >
            {{ item.label }}
          </button>
        </div>
        <el-select v-model="filter.serverId" clearable filterable placeholder="全部服务器" style="width: 180px" @change="onFilterChange">
          <el-option v-for="s in servers" :key="s.id" :label="s.name" :value="s.id" />
        </el-select>
      </div>
      <el-table v-loading="loading" :data="sessions" empty-text="暂无下载记录">
        <el-table-column label="服务器" width="160">
          <template #default="{ row }">
            {{ serverNameMap[row.serverId] || `#${row.serverId}` }}
          </template>
        </el-table-column>
        <el-table-column label="远端路径" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="font-mono text-small">{{ row.remotePath }}</span>
          </template>
        </el-table-column>
        <el-table-column label="进度" min-width="220">
          <template #default="{ row }">
            <div v-if="row.status === 'DOWNLOADING' || row.status === 'PENDING'" class="dl-progress">
              <span class="dl-bar"><i :style="{ transform: `scaleX(${progressPct(row) / 100})` }"></i></span>
              <span class="font-mono">{{ progressPct(row) }}%</span>
              <span class="page-note">{{ formatBytes(row.bytesReceived) }} / {{ formatBytes(row.fileSize) }}</span>
            </div>
            <span v-else class="font-mono">{{ formatBytes(row.bytesReceived) }} / {{ formatBytes(row.fileSize) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <span :class="downloadStatusClass(row.status)">{{ statusOptions.find(item => item.value === row.status)?.label || row.status }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="md5" label="MD5" width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.md5" class="font-mono text-small">{{ row.md5.substring(0, 16) }}…</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="errorMessage" label="错误信息" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.errorMessage || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170">
          <template #default="{ row }">
            <span class="font-mono">{{ row.createdAt }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="188" class-name="col-actions" align="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'SUCCESS'"
                       text type="primary" :icon="Download" @click="handleDownload(row)">取文件</el-button>
            <el-button v-if="row.status === 'DOWNLOADING' || row.status === 'PENDING'"
                       text type="warning" :icon="CircleClose" @click="handleCancel(row)">取消</el-button>
            <el-button text type="danger" :icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="console-foot">
        <el-pagination
          :total="total"
          :page-size="page.limit"
          :current-page="Math.floor(page.offset / page.limit) + 1"
          layout="total, prev, pager, next"
          @current-change="(p) => onPageChange({ pageNum: p, pageSize: page.limit })"
        />
      </div>
    </section>

    <!-- 触发下载对话框 -->
    <el-dialog v-model="triggerDialogVisible" width="520" destroy-on-close>
      <template #header>
        <div>
          <h2 class="od-dialog-title">触发下载</h2>
          <p class="od-dialog-sub">从远端 Agent 拉取文件到本地</p>
        </div>
      </template>
      <el-form class="od-form" :model="triggerForm" label-position="top">
        <div class="od-field-row">
          <el-form-item label="分组">
            <el-select v-model="triggerForm.groupId" placeholder="全部分组" style="width: 100%" @change="onTriggerGroupChange">
              <el-option label="全部分组" :value="null" />
              <el-option v-for="g in groups" :key="g.id" :label="g.name" :value="g.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="服务器" required>
            <el-select v-model="triggerForm.serverId" placeholder="选择服务器" style="width: 100%" filterable>
              <el-option v-for="s in triggerServerOptions" :key="s.id" :label="`${s.name} (${s.host})`" :value="s.id" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="远端绝对路径" required>
          <el-input v-model="triggerForm.remotePath" class="font-mono" placeholder="/var/log/order-service/app.log" />
        </el-form-item>
        <p class="od-notice">路径必须在 Agent 白名单内，Agent 会拒绝白名单之外的绝对路径。</p>
      </el-form>
      <template #footer>
        <span class="od-dialog-note">下载完成后可校验 MD5 并取回文件</span>
        <div class="od-dialog-actions">
          <el-button @click="triggerDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="triggering" @click="handleTrigger">开始下载</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
code {
  background: var(--el-fill-color-light);
  padding: 1px 4px;
  border-radius: 3px;
  font-family: var(--el-font-family-mono);
}
</style>
