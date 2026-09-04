<script setup>
import { ref, onMounted, computed } from 'vue'
import api from '../api/client'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, Promotion, BellFilled, Refresh, Search } from '@element-plus/icons-vue'

const activeTab = ref('configs')

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

function channelTypeTag(type) {
  const map = { dingtalk: 'primary', wechat_work: 'success', webhook: 'warning', email: 'info' }
  return map[type] || ''
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

function statusTag(s) {
  if (s === 'success') return 'success'
  if (s === 'failed') return 'danger'
  return 'info'
}

onMounted(() => {
  loadAll()
  loadHistory()
})
</script>

<template>
  <div class="page-container">
    <div class="page-toolbar">
      <h2 class="page-title">通知管理</h2>
      <el-button v-if="activeTab === 'configs'" type="primary" :icon="Plus" @click="handleAdd">新增通知配置</el-button>
      <el-button v-else :icon="Refresh" @click="loadHistory">刷新</el-button>
    </div>

    <el-tabs v-model="activeTab" class="page-tabs" @tab-change="(t) => t === 'history' && loadHistory()">
      <el-tab-pane label="通知配置" name="configs">
        <el-card shadow="never" class="table-card">
          <el-table v-loading="loading" :data="configs" stripe empty-text="暂无通知配置，点右上角新增">
            <el-table-column prop="id" label="ID" width="80">
              <template #default="{ row }">
                <span class="font-mono">{{ row.id }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="name" label="名称" min-width="160">
              <template #default="{ row }">
                <span class="fw-medium">{{ row.name }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="channelType" label="通道" width="140">
              <template #default="{ row }">
                <el-tag :type="channelTypeTag(row.channelType)" size="small">{{ channelName(row.channelType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="订阅事件" min-width="200">
              <template #default="{ row }">
                <el-tag v-for="t in safeParseArray(row.eventTypes)" :key="t" size="small" class="me-1 mb-1">
                  {{ eventName(t) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="启用" width="80">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
                  {{ row.enabled ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="260" fixed="right">
              <template #default="{ row }">
                <el-button text type="primary" :icon="Edit" @click="handleEdit(row)">编辑</el-button>
                <el-button text type="success" :icon="Promotion" @click="handleTest(row)">测试</el-button>
                <el-button text type="danger" :icon="Delete" @click="handleDelete(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="发送历史" name="history">
        <el-card shadow="never" class="filter-card mb-3">
          <el-form inline>
            <el-form-item label="配置">
              <el-select v-model="historyFilters.configId" clearable placeholder="全部" style="width: 200px" @change="onHistoryFilterChange">
                <el-option v-for="c in configs" :key="c.id" :label="c.name" :value="c.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="事件">
              <el-select v-model="historyFilters.eventType" clearable placeholder="全部" style="width: 160px" @change="onHistoryFilterChange">
                <el-option v-for="e in eventTypes" :key="e.type" :label="e.name" :value="e.type" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="historyFilters.status" clearable placeholder="全部" style="width: 120px" @change="onHistoryFilterChange">
                <el-option label="成功" value="success" />
                <el-option label="失败" value="failed" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button :icon="Search" @click="loadHistory">查询</el-button>
            </el-form-item>
          </el-form>
        </el-card>
        <el-card shadow="never" class="table-card">
          <el-table v-loading="historyLoading" :data="historyRows" stripe empty-text="暂无发送记录">
            <el-table-column prop="id" label="ID" width="80">
              <template #default="{ row }">
                <span class="font-mono">{{ row.id }}</span>
              </template>
            </el-table-column>
            <el-table-column label="事件" width="140">
              <template #default="{ row }">
                <el-tag size="small">{{ eventName(row.eventType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="通道" width="120">
              <template #default="{ row }">
                <el-tag :type="channelTypeTag(row.channelType)" size="small">{{ channelName(row.channelType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="taskId" label="任务ID" width="100">
              <template #default="{ row }">
                <span v-if="row.taskId" class="font-mono">{{ row.taskId }}</span>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="statusTag(row.status)" size="small">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="errorMessage" label="错误信息" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.errorMessage || '-' }}
              </template>
            </el-table-column>
            <el-table-column prop="sentAt" label="发送时间" width="170">
              <template #default="{ row }">
                <span class="font-mono">{{ row.sentAt }}</span>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            class="mt-3"
            :total="historyTotal"
            :page-size="historyPage.limit"
            :current-page="Math.floor(historyPage.offset / historyPage.limit) + 1"
            layout="total, prev, pager, next, jumper"
            @current-change="(p) => onHistoryPageChange({ pageNum: p, pageSize: historyPage.limit })"
          />
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 配置对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editing ? '编辑通知配置' : '新增通知配置'"
      width="720px"
      destroy-on-close
    >
      <el-form :model="dialogForm" label-width="100px">
        <el-form-item label="配置名称" required>
          <el-input v-model="dialogForm.name" placeholder="如：生产-钉钉告警群" />
        </el-form-item>
        <el-form-item label="通道类型" required>
          <el-select v-model="dialogForm.channelType" :disabled="!!editing" @change="onChannelTypeChange">
            <el-option v-for="c in channels" :key="c.type" :label="c.name" :value="c.type" />
          </el-select>
        </el-form-item>
        <el-form-item label="通道配置" required>
          <el-input
            v-model="dialogForm.channelConfig"
            type="textarea"
            :rows="6"
            placeholder="JSON 配置：钉钉示例 {&quot;webhookUrl&quot;:&quot;...&quot;,&quot;secret&quot;:&quot;...&quot;}"
          />
        </el-form-item>
        <el-form-item label="订阅事件" required>
          <el-checkbox-group v-model="dialogForm.eventTypes">
            <el-checkbox v-for="e in eventTypes" :key="e.type" :value="e.type">
              {{ e.name }} <span class="text-secondary font-mono">({{ e.type }})</span>
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="限定服务器组">
          <el-select v-model="dialogForm.serverGroupIds" multiple collapse-tags collapse-tags-tooltip
                     placeholder="留空表示所有服务器" style="width: 100%">
            <el-option v-for="g in groups" :key="g.id" :label="g.name" :value="g.id" />
          </el-select>
          <div class="form-hint">不选则对所有服务器事件触发</div>
        </el-form-item>
        <el-form-item label="限定任务模板">
          <el-select v-model="dialogForm.taskTemplateIds" multiple collapse-tags collapse-tags-tooltip
                     placeholder="留空表示所有任务" style="width: 100%">
            <el-option v-for="t in tasks" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
          <div class="form-hint">不选则对所有任务事件触发</div>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="dialogForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-tabs {
  margin-bottom: var(--spacing-3);
}
.filter-card {
  padding: var(--spacing-3);
}
.text-secondary {
  color: var(--el-text-color-secondary);
  font-size: 0.85em;
  margin-left: 4px;
}
.form-hint {
  font-size: 0.8em;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}
</style>
