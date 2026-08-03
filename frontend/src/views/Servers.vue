<script setup>
import { onMounted, ref, computed } from 'vue'
import api from '../api/client'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete, Connection, Edit, Monitor, Cpu, Download } from '@element-plus/icons-vue'
import ServerDialog from '../components/ServerDialog.vue'
import DebugDialog from '../components/DebugDialog.vue'
import TerminalDialog from '../components/TerminalDialog.vue'
import AgentInstallGuide from '../components/AgentInstallGuide.vue'

const groups = ref([])
const servers = ref([])
const loading = ref(false)
const groupFilter = ref('')

const addDialogVisible = ref(false)
const debugDialogVisible = ref(false)
const terminalDialogVisible = ref(false)
const agentGuideVisible = ref(false)
const currentDebugServerId = ref(null)
const currentTerminalServerId = ref(null)

const filteredServers = computed(() => {
  if (!groupFilter.value) return servers.value
  return servers.value.filter(s => s.groupId === groupFilter.value)
})

function getGroupName(groupId) {
  if (!groups.value || !groupId) return '-'
  const g = groups.value.find(g => g.id === groupId)
  return g ? g.name : '-'
}

function hasSshConfig(server) {
  return (server.sshPassword && server.sshPassword.length > 0) ||
         (server.sshPrivateKey && server.sshPrivateKey.length > 0)
}

async function loadData() {
  loading.value = true
  try {
    const [groupsRes, serversRes] = await Promise.all([
      api.getGroups(),
      api.getServers()
    ])
    groups.value = groupsRes || []
    servers.value = serversRes || []
  } catch (error) {
    ElMessage.error('加载服务器失败')
    console.error(error)
  } finally {
    loading.value = false
  }
}

function handleAdd() {
  addDialogVisible.value = true
}

function openAgentGuide() {
  agentGuideVisible.value = true
}

function onGuideGotoAdd() {
  addDialogVisible.value = true
}

async function handleTest(server) {
  try {
    const result = await api.testServer(server.id)
    if (result?.connected) {
      ElMessage.success('连接成功')
    } else {
      ElMessage.warning('连接失败')
    }
    await loadData()
  } catch (error) {
    ElMessage.error('测试失败')
    console.error(error)
  }
}

function openDebug(server) {
  currentDebugServerId.value = server.id
  debugDialogVisible.value = true
}

function openTerminal(server) {
  currentTerminalServerId.value = server.id
  terminalDialogVisible.value = true
}

async function handleDelete(server) {
  try {
    await ElMessageBox.confirm(
      `确定要删除服务器「${server.name}」吗？`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
    await api.deleteServer(server.id)
    ElMessage.success('删除成功')
    await loadData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
      console.error(error)
    }
  }
}

function onDialogClose() {
  loadData()
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="page-container">
    <div class="page-toolbar">
      <h2 class="page-title">服务器管理</h2>
      <div class="toolbar-actions">
        <el-select
          v-if="groups.length > 1"
          v-model="groupFilter"
          placeholder="按分组筛选"
          clearable
          style="width: 160px"
        >
          <el-option
            v-for="g in groups"
            :key="g.id"
            :label="g.name"
            :value="g.id"
          />
        </el-select>
        <el-button :icon="Download" @click="openAgentGuide">
          下载 Agent
        </el-button>
        <el-button type="primary" :icon="Plus" @click="handleAdd">
          添加服务器
        </el-button>
      </div>
    </div>

    <el-card shadow="never" class="table-card">
      <el-table
        v-loading="loading"
        :data="filteredServers"
        stripe
        empty-text="暂无数据"
      >
        <el-table-column prop="id" label="ID" width="70">
          <template #default="{ row }">
            <span class="font-mono">{{ row.id }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="名称" min-width="120" />
        <el-table-column prop="host" label="地址" width="140">
          <template #default="{ row }">
            <span class="font-mono">{{ row.host }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="port" label="端口" width="80" />
        <el-table-column label="分组" width="180">
          <template #default="{ row }">
            {{ getGroupName(row.groupId) }}
          </template>
        </el-table-column>
        <el-table-column label="SSH" width="70" align="center">
          <template #default="{ row }">
            <el-icon v-if="row.sshUsername" color="var(--el-color-success)">
              <CircleCheck />
            </el-icon>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag
              :type="row.status === 'online' ? 'success' : 'danger'"
              effect="light"
              round
            >
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="400" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" :icon="Connection" @click="handleTest(row)">
              测试
            </el-button>
            <el-button text type="primary" :icon="Cpu" @click="openDebug(row)">
              调试
            </el-button>
            <el-button
              text
              type="success"
              :icon="Monitor"
              :disabled="!row.sshUsername || !hasSshConfig(row)"
              @click="openTerminal(row)"
            >
              终端
            </el-button>
            <el-button text type="danger" :icon="Delete" @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <ServerDialog
      v-model="addDialogVisible"
      :groups="groups"
      @success="onDialogClose"
    />

    <DebugDialog
      v-model="debugDialogVisible"
      :server-id="currentDebugServerId"
    />

    <TerminalDialog
      v-model="terminalDialogVisible"
      :server-id="currentTerminalServerId"
    />

    <AgentInstallGuide
      v-model="agentGuideVisible"
      @goto-add="onGuideGotoAdd"
    />
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
  flex-wrap: wrap;
  gap: var(--spacing-3);
}

.toolbar-actions {
  display: flex;
  gap: var(--spacing-3);
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
</style>
