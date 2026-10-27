<script setup>
import { ref, onMounted, computed } from 'vue'
import api from '../api/client'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, Promotion, BellFilled, Refresh, Search } from '@element-plus/icons-vue'

const activeTab = ref('configs')
const keyword = ref('')

// ---- 配置 ----
const configs = ref([])
const channels = ref([])
const eventTypes = ref([])
const groups = ref([])
const tasks = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const editing = ref(null)
const dialogForm = ref(emptyForm())

function emptyForm() {
  return {
    name: '',
    channelType: 'dingtalk',
    channelConfig: '',
    eventTypes: ['TASK_FAILED'],
    serverGroupIds: [],
    taskTemplateIds: [],
    enabled: true
  }
}

const channelTemplates = {
  dingtalk: { webhookUrl: '', secret: '' },
  wechat_work: { webhookUrl: '' },
  webhook: { url: '', headers: { 'Content-Type': 'application/json' } },
  email: { from: '', to: '', cc: '', subjectPrefix: '[ReDeploy]' }
}

async function loadAll() {
  loading.value = true
  try {
    const [cfgs, ch, ev, grp, t] = await Promise.all([
      api.getNotificationConfigs(),
      api.getNotificationChannels(),
      api.getNotificationEventTypes(),
      api.getGroups().catch(() => []),
      api.getTasks().catch(() => [])
    ])
    configs.value = cfgs || []
    channels.value = ch || []
    eventTypes.value = ev || []
    groups.value = grp || []
    tasks.value = (t && t.rows) || t || []
  } catch (error) {
    ElMessage.error('加载通知配置失败')
    console.error(error)
  } finally {
    loading.value = false
  }
}

function channelName(type) {
  const c = channels.value.find(x => x.type === type)
  return c ? c.name : type
}

function eventName(type) {
  const e = eventTypes.value.find(x => x.type === type)
  return e ? e.name : type
}

const visibleConfigs = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  if (!q) return configs.value
  return configs.value.filter(cfg => {
    const events = safeParseArray(cfg.eventTypes).map(eventName).join(' ')
    return `${cfg.name} ${channelName(cfg.channelType)} ${events}`.toLowerCase().includes(q)
  })
})

function configName(id) {
  return configs.value.find(cfg => cfg.id === id)?.name || (id ? `配置#${id}` : '—')
}

async function toggleEnabled(cfg, enabled) {
  const previous = cfg.enabled
  cfg.enabled = enabled
  try {
    await api.updateNotificationConfig(cfg.id, {
      name: cfg.name,
      channelType: cfg.channelType,
      channelConfig: cfg.channelConfig,
      eventTypes: cfg.eventTypes,
      serverGroupIds: cfg.serverGroupIds,
      taskTemplateIds: cfg.taskTemplateIds,
      enabled
    })
  } catch (error) {
    cfg.enabled = previous
    ElMessage.error(error.response?.data?.message || '更新状态失败')
  }
}

function handleAdd() {
  editing.value = null
  dialogForm.value = emptyForm()
  dialogForm.value.channelConfig = JSON.stringify(channelTemplates.dingtalk, null, 2)
  dialogVisible.value = true
}

function handleEdit(cfg) {
  editing.value = cfg
  dialogForm.value = {
    name: cfg.name,
    channelType: cfg.channelType,
    channelConfig: cfg.channelConfig,
    eventTypes: safeParseArray(cfg.eventTypes),
    serverGroupIds: safeParseArray(cfg.serverGroupIds),
    taskTemplateIds: safeParseArray(cfg.taskTemplateIds),
    enabled: cfg.enabled
  }
  dialogVisible.value = true
}

function safeParseArray(json) {
  if (!json) return []
  try {
    const v = JSON.parse(json)
    return Array.isArray(v) ? v : []
  } catch { return [] }
}

function onChannelTypeChange() {
  dialogForm.value.channelConfig = JSON.stringify(channelTemplates[dialogForm.value.channelType] || {}, null, 2)
}

async function handleSubmit() {
  if (!dialogForm.value.name?.trim()) {
    ElMessage.warning('请输入配置名称')
    return
  }
  if (!dialogForm.value.eventTypes || dialogForm.value.eventTypes.length === 0) {
    ElMessage.warning('至少订阅一个事件')
    return
  }
  // 校验 channelConfig 是合法 JSON
  try {
    JSON.parse(dialogForm.value.channelConfig)
  } catch {
    ElMessage.error('通道配置不是合法 JSON')
    return
  }
  const payload = {
    name: dialogForm.value.name,
    channelType: dialogForm.value.channelType,
    channelConfig: dialogForm.value.channelConfig,
    eventTypes: JSON.stringify(dialogForm.value.eventTypes),
    serverGroupIds: dialogForm.value.serverGroupIds.length ? JSON.stringify(dialogForm.value.serverGroupIds) : null,
    taskTemplateIds: dialogForm.value.taskTemplateIds.length ? JSON.stringify(dialogForm.value.taskTemplateIds) : null,
    enabled: dialogForm.value.enabled
  }
  try {
    if (editing.value) {
      await api.updateNotificationConfig(editing.value.id, payload)
      ElMessage.success('更新成功')
    } else {
      await api.createNotificationConfig(payload)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    await loadAll()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '保存失败')
  }
}

async function handleDelete(cfg) {
  try {
    await ElMessageBox.confirm(`确定要删除通知配置「${cfg.name}」吗？`, '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
    await api.deleteNotificationConfig(cfg.id)
    ElMessage.success('删除成功')
    await loadAll()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
      console.error(error)
    }
  }
}

async function handleTest(cfg) {
  try {
    const { value: body } = await ElMessageBox.prompt('测试消息正文（可留空使用默认）', '测试发送',
      { confirmButtonText: '发送', cancelButtonText: '取消', inputPlaceholder: '这是一条测试消息' })
    const resp = await api.testNotificationConfig(cfg.id, body)
    if (resp.success) {
      ElMessage.success('已发送')
    } else {
      ElMessage.error('发送失败: ' + resp.message)
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('测试发送失败')
      console.error(error)
    }
  }
}

// ---- 历史 ----
const historyRows = ref([])
const historyTotal = ref(0)
const historyLoading = ref(false)
const historyFilters = ref({ configId: null, eventType: null, status: null })
const historyPage = ref({ limit: 20, offset: 0 })

async function loadHistory() {
  historyLoading.value = true
  try {
    const params = {
      ...historyFilters.value,
      limit: historyPage.value.limit,
      offset: historyPage.value.offset
    }
    // 去掉空值
    Object.keys(params).forEach(k => (params[k] === null || params[k] === '') && delete params[k])
    const resp = await api.getNotificationHistory(params)
    historyRows.value = resp.rows || []
    historyTotal.value = resp.total || 0
  } catch (error) {
    ElMessage.error('加载历史失败')
    console.error(error)
  } finally {
    historyLoading.value = false
  }
}

function onHistoryPageChange(p) {
  historyPage.value.limit = p.pageSize
  historyPage.value.offset = (p.pageNum - 1) * p.pageSize
  loadHistory()
}

function onHistoryFilterChange() {
  historyPage.value.offset = 0
  loadHistory()
}

function notifyStatusClass(status) {
  if (status === 'success') return 'rd-status rd-ok'
  if (status === 'failed') return 'rd-status rd-fail'
  return 'rd-status rd-off'
}

function notifyStatusText(status) {
  if (status === 'success') return '成功'
  if (status === 'failed') return '失败'
  return status || '-'
}

onMounted(() => {
  loadAll()
  loadHistory()
})
</script>

<template>
  <div class="page-container">
    <div class="page-toolbar">
      <div class="od-segmented" role="tablist">
        <button type="button" :class="{ 'is-on': activeTab === 'configs' }" @click="activeTab = 'configs'">通知配置</button>
        <button type="button" :class="{ 'is-on': activeTab === 'history' }" @click="activeTab = 'history'; loadHistory()">发送历史</button>
      </div>
    </div>

    <section class="console-panel">
      <div class="console-toolbar">
        <template v-if="activeTab === 'configs'">
          <el-input v-model="keyword" class="console-search" clearable placeholder="搜索名称、渠道或事件" style="margin-left: 0" />
          <el-button type="primary" :icon="Plus" @click="handleAdd">新增通知</el-button>
        </template>
        <template v-else>
          <el-select v-model="historyFilters.configId" clearable placeholder="全部配置" style="width: 160px" @change="onHistoryFilterChange">
            <el-option v-for="c in configs" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
          <el-select v-model="historyFilters.eventType" clearable placeholder="全部事件" style="width: 140px" @change="onHistoryFilterChange">
            <el-option v-for="e in eventTypes" :key="e.type" :label="e.name" :value="e.type" />
          </el-select>
          <el-select v-model="historyFilters.status" clearable placeholder="全部状态" style="width: 120px" @change="onHistoryFilterChange">
            <el-option label="成功" value="success" />
            <el-option label="失败" value="failed" />
          </el-select>
          <el-button :icon="Refresh" @click="loadHistory">刷新</el-button>
        </template>
      </div>

      <el-table v-if="activeTab === 'configs'" v-loading="loading" :data="visibleConfigs" empty-text="未找到匹配的通知配置">
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column label="渠道" width="120">
          <template #default="{ row }">{{ channelName(row.channelType) }}</template>
        </el-table-column>
        <el-table-column label="事件" min-width="200">
          <template #default="{ row }">
            {{ safeParseArray(row.eventTypes).map(eventName).join('、') || '—' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="150">
          <template #default="{ row }">
            <el-switch :model-value="!!row.enabled" @change="(value) => toggleEnabled(row, value)" />
            <span class="page-note" style="margin-left: 8px">{{ row.enabled ? '启用' : '停用' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="196" class-name="col-actions" align="right">
          <template #default="{ row }">
            <el-button text :icon="Edit" @click="handleEdit(row)">编辑</el-button>
            <el-button text :icon="Promotion" @click="handleTest(row)">测试</el-button>
            <el-button text type="danger" :icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <template v-else>
        <el-table v-loading="historyLoading" :data="historyRows" empty-text="暂无发送记录">
          <el-table-column label="时间" width="180">
            <template #default="{ row }"><span class="font-mono">{{ row.sentAt }}</span></template>
          </el-table-column>
          <el-table-column label="配置" min-width="140">
            <template #default="{ row }">{{ row.configName || configName(row.configId) }}</template>
          </el-table-column>
          <el-table-column label="事件" min-width="120">
            <template #default="{ row }">{{ eventName(row.eventType) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <span :class="notifyStatusClass(row.status)">{{ notifyStatusText(row.status) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="摘要" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">{{ row.errorMessage || row.summary || '已发送' }}</template>
          </el-table-column>
        </el-table>
        <div class="console-foot">
          <el-pagination
            :total="historyTotal"
            :page-size="historyPage.limit"
            :current-page="Math.floor(historyPage.offset / historyPage.limit) + 1"
            layout="total, prev, pager, next"
            @current-change="(p) => onHistoryPageChange({ pageNum: p, pageSize: historyPage.limit })"
          />
        </div>
      </template>
    </section>

    <!-- 配置对话框 -->
    <el-dialog v-model="dialogVisible" width="560" destroy-on-close>
      <template #header>
        <div>
          <h2 class="od-dialog-title">{{ editing ? '编辑通知' : '新增通知' }}</h2>
          <p class="od-dialog-sub">按事件与渠道推送部署状态</p>
        </div>
      </template>
      <el-form class="od-form" :model="dialogForm" label-position="top">
        <el-form-item label="通知名称" required>
          <el-input v-model="dialogForm.name" maxlength="30" placeholder="如：生产-钉钉告警群" />
        </el-form-item>
        <div class="od-field-row">
          <el-form-item label="渠道" required>
            <el-select v-model="dialogForm.channelType" :disabled="!!editing" style="width: 100%" @change="onChannelTypeChange">
              <el-option v-for="c in channels" :key="c.type" :label="c.name" :value="c.type" />
            </el-select>
          </el-form-item>
          <el-form-item label="启用">
            <div class="od-jenkins-head" style="border: 1px solid var(--el-border-color); border-radius: 4px; background: transparent">
              <el-switch v-model="dialogForm.enabled" />
              <span class="page-note">{{ dialogForm.enabled ? '已启用' : '未启用' }}</span>
            </div>
          </el-form-item>
        </div>
        <el-form-item label="通道配置" required>
          <el-input
            v-model="dialogForm.channelConfig"
            type="textarea"
            :rows="5"
            class="font-mono"
            placeholder="JSON，例如钉钉 webhookUrl 与 secret"
          />
        </el-form-item>
        <el-form-item label="订阅事件" required>
          <el-checkbox-group v-model="dialogForm.eventTypes">
            <el-checkbox v-for="e in eventTypes" :key="e.type" :value="e.type">{{ e.name }}</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <div class="od-field-row">
          <el-form-item label="限定服务器组">
            <el-select v-model="dialogForm.serverGroupIds" multiple collapse-tags placeholder="留空表示全部" style="width: 100%">
              <el-option v-for="g in groups" :key="g.id" :label="g.name" :value="g.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="限定任务">
            <el-select v-model="dialogForm.taskTemplateIds" multiple collapse-tags placeholder="留空表示全部" style="width: 100%">
              <el-option v-for="t in tasks" :key="t.id" :label="t.name" :value="t.id" />
            </el-select>
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <span class="od-dialog-note">新增的通知默认启用</span>
        <div class="od-dialog-actions">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSubmit">保存</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-tabs {
  margin-bottom: var(--spacing-3);
}

/* 复选框标签里的事件类型后缀，仅需与名称拉开间距；颜色/字号走全局 .text-secondary */
.text-secondary {
  margin-left: 4px;
}
</style>
