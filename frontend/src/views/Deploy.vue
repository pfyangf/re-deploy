<script setup>
import { onMounted, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import api from '../api/client'
import { ElMessage } from 'element-plus'
import { Promotion } from '@element-plus/icons-vue'

const router = useRouter()
const tasks = ref([])
const servers = ref([])
const groups = ref([])
const loading = ref(false)
const submitting = ref(false)

const form = ref({
  taskId: '',
  serverIds: [],
  version: ''
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

async function loadData() {
  loading.value = true
  try {
    const [tasksRes, serversRes, groupsRes] = await Promise.all([
      api.getTasks(),
      api.getServers(),
      api.getGroups()
    ])
    tasks.value = tasksRes || []
    servers.value = serversRes || []
    groups.value = groupsRes || []
  } catch (error) {
    ElMessage.error('加载数据失败')
    console.error(error)
  } finally {
    loading.value = false
  }
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

  submitting.value = true
  try {
    const result = await api.createDeploy({
      taskId: parseInt(form.value.taskId),
      serverIds: form.value.serverIds,
      version: form.value.version
    })
    if (result && result.deployId) {
      ElMessage.success(`部署已启动，ID: ${result.deployId}`)
      router.push('/history')
    }
  } catch (error) {
    ElMessage.error('启动部署失败')
    console.error(error)
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="page-toolbar">
      <h2 class="page-title">部署操作</h2>
    </div>

    <el-card shadow="never" class="form-card">
      <el-form :model="form" label-width="100px" label-position="right">
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

.server-checkbox {
  margin-right: 0;
}

.server-host {
  color: var(--el-text-color-secondary);
  font-size: var(--text-sm);
  margin-left: var(--spacing-1);
}
</style>
