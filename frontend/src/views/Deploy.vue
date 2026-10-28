<script setup>
import { onMounted, ref, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import api from '../api/client'
import { ElMessage } from 'element-plus'
import { Promotion, Refresh } from '@element-plus/icons-vue'
import { saveDeployRecord, getDeployHistoryByTaskIds } from '../utils/deployHistory'

const router = useRouter()
const tasks = ref([])
const servers = ref([])
const groups = ref([])
const loading = ref(false)
const submitting = ref(false)

const selectedGroupId = ref(null)

const form = ref({
  taskId: '',
  serverIds: [],
  version: '',
  jenkinsBuildNumber: ''
})

const deployHistory = ref([])

const buildHistoryVisible = ref(false)
const buildHistoryLoading = ref(false)
const buildHistory = ref([])

// Get selected task object
const selectedTask = computed(() => {
  if (!form.value.taskId) return null
  return tasks.value.find(t => t.id === parseInt(form.value.taskId))
})

// Check if selected task has Jenkins enabled
const showJenkinsBuildNumber = computed(() => {
  return selectedTask.value && selectedTask.value.jenkinsEnabled
})

const isAllGroups = computed(() => selectedGroupId.value === null)

const groupOptions = computed(() => {
  return [
    { id: null, name: '全部分组' },
    ...groups.value
  ]
})

function getGroupName(groupId) {
  if (!groups.value || !groupId) return '默认分组'
  const g = groups.value.find(g => g.id === groupId)
  return g ? g.name : '默认分组'
}

const groupedServers = computed(() => {
  const grouped = {}
  if (!servers.value.length) return grouped
  servers.value.forEach(s => {
    const gid = s.groupId || 0
    if (!grouped[gid]) grouped[gid] = []
    grouped[gid].push(s)
  })
  return grouped
})

async function loadGroups() {
  try {
    const res = await api.getGroups()
    groups.value = res || []
  } catch (error) {
    ElMessage.error('加载分组失败')
    console.error(error)
  }
}

async function loadTasksAndServers() {
  loading.value = true
  try {
    const params = isAllGroups.value ? {} : { groupId: selectedGroupId.value }
    const [tasksRes, serversRes] = await Promise.all([
      api.getTasks(params),
      api.getServers(params)
    ])
    tasks.value = tasksRes || []
    servers.value = serversRes || []
  } catch (error) {
    ElMessage.error('加载数据失败')
    console.error(error)
  } finally {
    loading.value = false
  }
}

async function loadData() {
  await loadGroups()
  await loadTasksAndServers()
}

function onGroupChange() {
  form.value.taskId = ''
  form.value.serverIds = []
  buildHistoryVisible.value = false
  buildHistory.value = []
  loadTasksAndServers()
}

async function fetchBuildHistory() {
  if (!selectedTask.value) return
  buildHistoryLoading.value = true
  buildHistoryVisible.value = true
  buildHistory.value = []
  try {
    const res = await api.getJenkinsBuildHistory(selectedTask.value.id)
    buildHistory.value = res || []
  } catch (error) {
    ElMessage.error('拉取构建历史失败')
    console.error(error)
  } finally {
    buildHistoryLoading.value = false
  }
}

function selectBuild(build) {
  form.value.jenkinsBuildNumber = String(build.number)
  buildHistoryVisible.value = false
}

function applyHistoryRecord(record) {
  form.value.taskId = record.taskId
  form.value.serverIds = [...record.serverIds]
  form.value.version = record.version || ''
  form.value.jenkinsBuildNumber = record.jenkinsBuildNumber || ''
}

function formatRelativeTime(timestamp) {
  if (!timestamp) return ''
  const date = new Date(timestamp)
  const now = new Date()
  const diffMs = now - date
  const diffMins = Math.floor(diffMs / 60000)
  const diffHours = Math.floor(diffMs / 3600000)
  const diffDays = Math.floor(diffMs / 86400000)
  if (diffMins < 1) return '刚刚'
  if (diffMins < 60) return `${diffMins} 分钟前`
  if (diffHours < 24) return `${diffHours} 小时前`
  if (diffDays < 7) return `${diffDays} 天前`
  return date.toLocaleDateString('zh-CN')
}

function formatBuildTime(timestamp) {
  if (!timestamp) return ''
  const date = new Date(timestamp)
  const now = new Date()
  const diffMs = now - date
  const diffMins = Math.floor(diffMs / 60000)
  const diffHours = Math.floor(diffMs / 3600000)
  const diffDays = Math.floor(diffMs / 86400000)
  if (diffMins < 1) return '刚刚'
  if (diffMins < 60) return `${diffMins} 分钟前`
  if (diffHours < 24) return `${diffHours} 小时前`
  if (diffDays < 7) return `${diffDays} 天前`
  return date.toLocaleDateString('zh-CN')
}

function getResultTagType(result) {
  if (!result || result === 'BUILDING') return 'warning'
  if (result === 'SUCCESS') return 'success'
  return 'danger'
}

function getResultText(result) {
  if (!result || result === 'BUILDING') return '构建中'
  if (result === 'SUCCESS') return '成功'
  if (result === 'FAILURE') return '失败'
  return result
}

async function handleSubmit() {
  if (!form.value.taskId) {
    ElMessage.warning('请选择任务')
    return
  }
  if (form.value.serverIds.length === 0) {
    ElMessage.warning('请至少选择一个服务器')
    return
  }
  // If Jenkins enabled, require build number
  if (showJenkinsBuildNumber.value && !form.value.jenkinsBuildNumber) {
    ElMessage.warning('任务启用了 Jenkins，请输入构建号')
    return
  }

  submitting.value = true
  try {
    saveDeployRecord({
      taskId: parseInt(form.value.taskId),
      taskName: selectedTask.value ? selectedTask.value.name : '',
      groupId: selectedGroupId.value,
      groupName: getGroupName(selectedGroupId.value),
      serverIds: form.value.serverIds,
      version: form.value.version,
      jenkinsBuildNumber: form.value.jenkinsBuildNumber
    })

    const requestData = {
      taskId: parseInt(form.value.taskId),
      serverIds: form.value.serverIds,
      version: form.value.version,
      groupId: selectedGroupId.value,
      params: {}
    }
    // Add jenkins build number to params if present
    if (form.value.jenkinsBuildNumber) {
      requestData.params.jenkinsBuildNumber = form.value.jenkinsBuildNumber
    }
    const result = await api.createDeploy(requestData)
    if (result && result.deployId) {
      ElMessage.success(
        (result.status === 'pending' ? '部署已排队，ID: ' : '部署已启动，ID: ') + result.deployId
      )
      router.push('/history')
    }
  } catch (error) {
    const msg = error?.response?.data?.error || '启动部署失败'
    ElMessage.error(msg)
    console.error(error)
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadData()
})

watch(() => tasks.value, () => {
  refreshDeployHistory()
}, { deep: true })

watch(selectedGroupId, () => {
  refreshDeployHistory()
})

function refreshDeployHistory() {
  if (selectedGroupId.value !== null && tasks.value.length) {
    deployHistory.value = getDeployHistoryByTaskIds(tasks.value.map(t => t.id))
  } else {
    deployHistory.value = []
  }
}

const selectedServers = computed(() =>
  servers.value.filter(s => form.value.serverIds.includes(s.id))
)

const onlineSelectedCount = computed(() =>
  selectedServers.value.filter(s => s.status === 'online').length
)

function selectOnlineServers() {
  form.value.serverIds = servers.value
    .filter(s => s.status === 'online')
    .map(s => s.id)
}

function clearSelectedServers() {
  form.value.serverIds = []
}
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="page-toolbar">
      <h2 class="page-title">新建部署</h2>
      <span class="page-note">选择目标、任务和版本后发起</span>
    </div>

    <div v-if="deployHistory.length" class="quick-fill">
      <span class="quick-fill-title">最近部署</span>
      <button
        v-for="record in deployHistory"
        :key="record.timestamp"
        type="button"
        class="rd-chip"
        @click="applyHistoryRecord(record)"
      >
        {{ record.taskName || '未知任务' }}
        <span class="rd-chip-count">{{ record.serverIds.length }} 台 · {{ formatRelativeTime(record.timestamp) }}</span>
      </button>
    </div>

    <div class="deploy-grid">
      <div class="deploy-form">
        <section class="step-panel">
          <header class="step-head">
            <h3><span class="step-no">01</span>选择目标</h3>
            <span>先定分组，再勾选服务器</span>
          </header>
          <div class="step-body">
            <div class="group-grid">
              <label
                v-for="group in groupOptions"
                :key="group.id ?? 'all'"
                class="choice-row"
                :class="{ 'is-selected': selectedGroupId === group.id }"
              >
                <input
                  type="radio"
                  name="deploy-group"
                  :checked="selectedGroupId === group.id"
                  @change="selectedGroupId = group.id; onGroupChange()"
                >
                <span>
                  <span class="choice-name">{{ group.name }}</span>
                  <span class="choice-meta">{{ group.id == null ? '不限制分组' : '仅该分组下的任务和服务器' }}</span>
                </span>
              </label>
            </div>

            <div class="pick-toolbar">
              <span>已选 <b>{{ form.serverIds.length }}</b> 台<span v-if="onlineSelectedCount"> · {{ onlineSelectedCount }} 在线</span></span>
              <span class="pick-actions">
                <button type="button" class="text-btn" @click="selectOnlineServers">全选在线</button>
                <button type="button" class="text-btn" @click="clearSelectedServers">清空</button>
              </span>
            </div>

            <div v-if="servers.length" class="server-pick">
              <template v-if="isAllGroups">
                <div v-for="(groupServers, gid) in groupedServers" :key="gid" class="pick-group">
                  <div class="pick-group-name">{{ getGroupName(parseInt(gid)) }}</div>
                  <label v-for="server in groupServers" :key="server.id" class="check-row">
                    <input v-model="form.serverIds" type="checkbox" :value="server.id">
                    <span class="check-name">{{ server.name }}</span>
                    <span class="check-meta font-mono">{{ server.host }}:{{ server.port }}</span>
                    <span :class="server.status === 'online' ? 'rd-status rd-ok' : 'rd-status rd-off'">
                      {{ server.status === 'online' ? '在线' : '离线' }}
                    </span>
                  </label>
                </div>
              </template>
              <template v-else>
                <label v-for="server in servers" :key="server.id" class="check-row">
                  <input v-model="form.serverIds" type="checkbox" :value="server.id">
                  <span class="check-name">{{ server.name }}</span>
                  <span class="check-meta font-mono">{{ server.host }}:{{ server.port }}</span>
                  <span :class="server.status === 'online' ? 'rd-status rd-ok' : 'rd-status rd-off'">
                    {{ server.status === 'online' ? '在线' : '离线' }}
                  </span>
                </label>
              </template>
            </div>
            <p v-else class="empty-note">当前分组没有服务器</p>
          </div>
        </section>

        <section class="step-panel">
          <header class="step-head">
            <h3><span class="step-no">02</span>选择任务</h3>
            <span>使用已保存的任务模板</span>
          </header>
          <div class="step-body">
            <div v-if="tasks.length" class="task-pick">
              <label
                v-for="task in tasks"
                :key="task.id"
                class="choice-row"
                :class="{ 'is-selected': form.taskId === task.id }"
              >
                <input v-model="form.taskId" type="radio" name="deploy-task" :value="task.id">
                <span>
                  <span class="choice-name">{{ task.name }}</span>
                  <span class="choice-meta">{{ task.jenkinsEnabled ? '需要 Jenkins 构建号' : '直接执行任务脚本' }}</span>
                </span>
              </label>
            </div>
            <p v-else class="empty-note">当前分组没有任务</p>
          </div>
        </section>

        <section class="step-panel">
          <header class="step-head">
            <h3><span class="step-no">03</span>部署参数</h3>
            <span>版本号写入部署历史</span>
          </header>
          <div class="step-body form-grid">
            <label class="field">
              <span>版本号</span>
              <el-input v-model="form.version" placeholder="例如 v1.0.0" />
            </label>
            <label v-if="showJenkinsBuildNumber" class="field">
              <span>Jenkins 构建号</span>
              <div class="jenkins-build-row">
                <el-input v-model="form.jenkinsBuildNumber" placeholder="例如 164" />
                <el-popover
                  v-model:visible="buildHistoryVisible"
                  placement="bottom-start"
                  trigger="manual"
                  :width="400"
                >
                  <template #reference>
                    <el-button :icon="Refresh" :loading="buildHistoryLoading" @click="fetchBuildHistory">
                      拉取
                    </el-button>
                  </template>
                  <div class="build-history-panel">
                    <div class="build-history-title">构建历史</div>
                    <div v-if="buildHistoryLoading" class="build-history-loading">加载中...</div>
                    <div v-else-if="buildHistory.length === 0" class="build-history-empty">暂无构建记录</div>
                    <div v-else class="build-history-list">
                      <div
                        v-for="build in buildHistory"
                        :key="build.number"
                        class="build-history-item"
                        @click="selectBuild(build)"
                      >
                        <span class="build-number">#{{ build.number }}</span>
                        <el-tag :type="getResultTagType(build.result)" size="small">
                          {{ getResultText(build.result) }}
                        </el-tag>
                        <span class="build-time">{{ formatBuildTime(build.timestamp) }}</span>
                      </div>
                    </div>
                  </div>
                </el-popover>
              </div>
            </label>
          </div>
        </section>
      </div>

      <aside class="step-panel deploy-aside">
        <header class="step-head">
          <h3>本次部署</h3>
          <span>待提交</span>
        </header>
        <div class="step-body">
          <dl class="kv">
            <div><dt>分组</dt><dd>{{ getGroupName(selectedGroupId) }}</dd></div>
            <div><dt>任务</dt><dd>{{ selectedTask ? selectedTask.name : '未选择' }}</dd></div>
            <div><dt>目标</dt><dd>{{ form.serverIds.length ? form.serverIds.length + ' 台服务器' : '尚未选择' }}</dd></div>
            <div><dt>版本</dt><dd class="font-mono">{{ form.version || '—' }}</dd></div>
            <div v-if="showJenkinsBuildNumber"><dt>构建号</dt><dd class="font-mono">{{ form.jenkinsBuildNumber || '—' }}</dd></div>
          </dl>
          <p class="aside-note">
            {{ form.serverIds.length && form.taskId ? '提交后跳转到部署历史，可查看各机器日志。' : '请先选择任务，并至少勾选一台服务器。' }}
          </p>
          <el-button
            type="primary"
            class="submit-btn"
            :icon="Promotion"
            :loading="submitting"
            @click="handleSubmit"
          >
            发起部署
          </el-button>
        </div>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.quick-fill {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.quick-fill-title {
  font-size: 11.5px;
  color: var(--el-text-color-secondary);
}

.deploy-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(280px, 0.7fr);
  gap: 12px;
  align-items: start;
}

.deploy-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}

.step-panel {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
}

.step-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  padding: 11px 16px;
  border-bottom: 1px solid var(--el-border-color);
}

.step-head h3 {
  margin: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
}

.step-head span,
.step-no {
  font-size: 11.5px;
  color: var(--el-text-color-secondary);
  font-weight: 500;
}

.step-no {
  font-family: var(--el-font-family-mono);
  letter-spacing: 0.04em;
}

.step-body {
  padding: 14px 16px;
}

.group-grid,
.task-pick {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.choice-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 9px 10px;
  border: 1px solid var(--el-border-color);
  border-radius: 5px;
  cursor: pointer;
}

.choice-row.is-selected {
  border-color: #9bb6d8;
  background: var(--el-fill-color-light);
}

.choice-name {
  display: block;
  font-size: 12.5px;
  font-weight: 600;
}

.choice-meta {
  display: block;
  margin-top: 2px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.pick-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  font-size: 11.5px;
  color: var(--el-text-color-secondary);
}

.pick-actions {
  display: flex;
  gap: 8px;
}

.text-btn {
  border: none;
  background: none;
  padding: 0;
  color: var(--el-text-color-regular);
  cursor: pointer;
  font-size: 11.5px;
}

.server-pick {
  margin-top: 8px;
  border: 1px solid var(--el-border-color);
  border-radius: 5px;
  max-height: 260px;
  overflow: auto;
}

.pick-group-name {
  padding: 6px 10px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
}

.check-row {
  display: grid;
  grid-template-columns: 16px minmax(0, 1fr) minmax(0, 1.2fr) auto;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  border-top: 1px solid var(--el-border-color);
  font-size: 12.5px;
}

.check-name {
  font-weight: 600;
}

.check-meta {
  color: var(--el-text-color-secondary);
  font-size: 11.5px;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.deploy-aside {
  position: sticky;
  top: 12px;
}

.kv {
  margin: 0;
}

.kv div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 7px 0;
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

.aside-note {
  margin: 12px 0;
  font-size: 11.5px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}

.submit-btn {
  width: 100%;
}

.empty-note {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

@media (max-width: 980px) {
  .deploy-grid,
  .group-grid,
  .task-pick,
  .form-grid {
    grid-template-columns: 1fr;
  }

  .deploy-aside {
    position: static;
  }
}

.server-groups {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-4);
}

.server-group {
  padding: var(--spacing-3);
  background-color: var(--el-fill-color-light);
  border-radius: var(--el-border-radius-base);
}

.group-title {
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: var(--spacing-3);
  padding-bottom: var(--spacing-2);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.server-list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-3);
}

.flat-list {
  padding: var(--spacing-3);
  background-color: var(--el-fill-color-light);
  border-radius: var(--el-border-radius-base);
}

.server-checkbox {
  margin-right: 0;
}

.server-host {
  color: var(--el-text-color-secondary);
  font-size: var(--text-sm);
  margin-left: var(--spacing-1);
}

.jenkins-build-row {
  display: flex;
  gap: var(--spacing-2);
  align-items: center;
}

.build-history-panel {
  max-height: 400px;
  overflow-y: auto;
}

.build-history-title {
  font-weight: 600;
  margin-bottom: 8px;
  color: var(--el-text-color-primary);
}

.build-history-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 20px 0;
  color: var(--el-text-color-secondary);
}

.build-history-empty {
  text-align: center;
  padding: 20px 0;
  color: var(--el-text-color-secondary);
}

.build-history-list {
  display: flex;
  flex-direction: column;
}

.build-history-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-radius: var(--el-border-radius-base);
  cursor: pointer;
  transition: background-color 0.2s;
}

.build-history-item:hover {
  background-color: var(--el-fill-color-light);
}

.build-number {
  font-weight: 600;
  font-family: var(--el-font-family-mono);
  min-width: 50px;
}

.build-time {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  margin-left: auto;
}

.build-desc {
  color: var(--el-text-color-primary);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 120px;
}

.quick-fill-section {
  margin-bottom: var(--spacing-4);
  padding-bottom: var(--spacing-4);
  border-bottom: 1px dashed var(--el-border-color-lighter);
}

.quick-fill-title {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-bottom: var(--spacing-3);
}

.quick-fill-list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-3);
}

.quick-fill-card {
  padding: var(--spacing-3);
  background-color: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
  cursor: pointer;
  transition: all 0.2s;
  min-width: 140px;
}

.quick-fill-card:hover {
  background-color: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-5);
}

.qf-task-name {
  font-size: 12px;
  color: var(--el-color-primary);
  margin-bottom: 4px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.qf-group {
  font-weight: 600;
  font-size: 14px;
  color: var(--el-text-color-primary);
  margin-bottom: 4px;
}

.qf-servers {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.qf-build {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  font-family: var(--el-font-family-mono);
}

.qf-time {
  font-size: 11px;
  color: var(--el-text-color-placeholder);
  margin-top: 4px;
}
</style>
