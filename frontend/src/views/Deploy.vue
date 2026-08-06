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
      params: {}
    }
    // Add jenkins build number to params if present
    if (form.value.jenkinsBuildNumber) {
      requestData.params.jenkinsBuildNumber = form.value.jenkinsBuildNumber
    }
    const result = await api.createDeploy(requestData)
    if (result && result.deployId) {
      ElMessage.success(`部署已启动，ID: ${result.deployId}`)
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
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="page-toolbar">
      <h2 class="page-title">部署操作</h2>
    </div>

    <el-card shadow="never" class="form-card">
      <div v-if="deployHistory.length" class="quick-fill-section">
        <div class="quick-fill-title">快捷填充（最近 {{ deployHistory.length }} 次部署）</div>
        <div class="quick-fill-list">
          <div
            v-for="record in deployHistory"
            :key="record.timestamp"
            class="quick-fill-card"
            @click="applyHistoryRecord(record)"
          >
            <div class="qf-task-name">{{ record.taskName || '未知任务' }}</div>
            <div class="qf-group">{{ record.groupName || '默认分组' }}</div>
            <div class="qf-servers">{{ record.serverIds.length }} 台服务器</div>
            <div v-if="record.jenkinsBuildNumber" class="qf-build">Jenkins #{{ record.jenkinsBuildNumber }}</div>
            <div class="qf-time">{{ formatRelativeTime(record.timestamp) }}</div>
          </div>
        </div>
      </div>

      <el-form :model="form" label-width="100px" label-position="right">
        <el-form-item label="选择分组">
          <el-select
            v-model="selectedGroupId"
            placeholder="请选择分组"
            style="width: 100%; max-width: 300px"
            @change="onGroupChange"
          >
            <el-option
              v-for="group in groupOptions"
              :key="group.id ?? 'all'"
              :label="group.name"
              :value="group.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="选择任务" required>
          <el-select
            v-model="form.taskId"
            placeholder="请选择任务"
            style="width: 100%; max-width: 400px"
          >
            <el-option
              v-for="task in tasks"
              :key="task.id"
              :label="task.name"
              :value="task.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="选择服务器" required>
          <div class="server-groups">
            <template v-if="servers.length">
              <template v-if="isAllGroups">
                <div
                  v-for="(groupServers, gid) in groupedServers"
                  :key="gid"
                  class="server-group"
                >
                  <div class="group-title">{{ getGroupName(parseInt(gid)) }}</div>
                  <el-checkbox-group v-model="form.serverIds" class="server-list">
                    <el-checkbox
                      v-for="server in groupServers"
                      :key="server.id"
                      :value="server.id"
                      class="server-checkbox"
                    >
                      {{ server.name }}
                      <span class="server-host font-mono">({{ server.host }})</span>
                    </el-checkbox>
                  </el-checkbox-group>
                </div>
              </template>
              <template v-else>
                <el-checkbox-group v-model="form.serverIds" class="server-list flat-list">
                  <el-checkbox
                    v-for="server in servers"
                    :key="server.id"
                    :value="server.id"
                    class="server-checkbox"
                  >
                    {{ server.name }}
                    <span class="server-host font-mono">({{ server.host }})</span>
                  </el-checkbox>
                </el-checkbox-group>
              </template>
            </template>
            <el-empty v-else description="暂无服务器" :image-size="80" />
          </div>
        </el-form-item>

        <el-form-item label="版本号">
          <el-input
            v-model="form.version"
            placeholder="例如: v1.0.0"
            style="max-width: 300px"
          />
        </el-form-item>

        <el-form-item v-if="showJenkinsBuildNumber" label="Jenkins 构建号">
          <div class="jenkins-build-row">
            <el-input
              v-model="form.jenkinsBuildNumber"
              placeholder="例如: 164"
              style="max-width: 300px"
            />
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
                <div v-if="buildHistoryLoading" class="build-history-loading">
                  <el-icon class="is-loading"><Refresh /></el-icon>
                  <span>加载中...</span>
                </div>
                <div v-else-if="buildHistory.length === 0" class="build-history-empty">
                  暂无构建记录
                </div>
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
                    <span v-if="build.description" class="build-desc">{{ build.description }}</span>
                  </div>
                </div>
              </div>
            </el-popover>
          </div>
          <div class="hint">输入要部署的构建编号，或点击"拉取"从 Jenkins 历史中选择</div>
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            size="large"
            :icon="Promotion"
            :loading="submitting"
            @click="handleSubmit"
          >
            开始部署
          </el-button>
        </el-form-item>
      </el-form>
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

.form-card {
  border-radius: var(--el-border-radius-round);
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

.hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
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
  font-family: monospace;
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
  font-family: monospace;
}

.qf-time {
  font-size: 11px;
  color: var(--el-text-color-placeholder);
  margin-top: 4px;
}
</style>
