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
const serverQuery = ref('')

const addDialogVisible = ref(false)
const debugDialogVisible = ref(false)
const terminalDialogVisible = ref(false)
const currentTerminalServer = ref(null)
const agentGuideVisible = ref(false)
const currentDebugServerId = ref(null)
const currentTerminalServerId = ref(null)
// 正在编辑的服务器；null 表示新增模式
const editingServer = ref(null)

const filteredServers = computed(() => {
  const q = serverQuery.value.trim().toLowerCase()
  return servers.value.filter(s => {
    if (groupFilter.value && s.groupId !== groupFilter.value) return false
    if (!q) return true
    return `${s.name} ${s.host} ${s.port}`.toLowerCase().includes(q)
  })
})

const onlineTotal = computed(() => servers.value.filter(s => s.status === 'online').length)

const groupChips = computed(() => {
  const chips = [{ id: '', name: '全部', count: servers.value.length }]
  groups.value.forEach(group => {
    chips.push({
      id: group.id,
      name: group.name,
      count: servers.value.filter(s => s.groupId === group.id).length
    })
  })
  return chips
})

function getGroupName(groupId) {
  if (!groups.value || !groupId) return '-'
  const g = groups.value.find(g => g.id === groupId)
  return g ? g.name : '-'
}

function hasSshConfig(server) {
  // 密文不再下发，后端返回是否已配置的标志位
  return !!(server.hasSshPassword || server.hasSshPrivateKey)
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
  editingServer.value = null
  addDialogVisible.value = true
}

function handleEdit(server) {
  editingServer.value = server
  addDialogVisible.value = true
}

function openAgentGuide() {
  agentGuideVisible.value = true
}

function onGuideGotoAdd() {
  handleAdd()
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
  currentTerminalServer.value = server
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
  editingServer.value = null
  loadData()
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="page-container">
    <div class="page-toolbar">
      <h2 class="page-title">服务器列表</h2>
      <span class="page-note">共 {{ filteredServers.length }} 台 · {{ onlineTotal }} 在线</span>
    </div>

    <section class="server-panel">
      <div class="server-toolbar">
        <div class="chips">
          <button
            v-for="chip in groupChips"
            :key="chip.id || 'all'"
            type="button"
            class="rd-chip"
            :class="{ 'is-on': groupFilter === chip.id }"
            @click="groupFilter = chip.id"
          >
            {{ chip.name }}
            <span class="rd-chip-count">{{ chip.count }}</span>
          </button>
        </div>
        <el-input v-model="serverQuery" class="server-search" clearable placeholder="搜索名称、主机或分组" />
      </div>
      <el-table
        v-loading="loading"
        :data="filteredServers"
        empty-text="没有匹配的服务器"
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
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <span :class="row.status === 'online' ? 'rd-status rd-ok' : 'rd-status rd-off'">
              {{ row.status === 'online' ? '在线' : '离线' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="292" class-name="col-actions" align="right">
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
            <el-button text type="primary" :icon="Edit" @click="handleEdit(row)">
              编辑
            </el-button>
            <el-button text type="danger" :icon="Delete" @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <footer class="panel-foot">
        <span>离线服务器不参与批量部署</span>
        <span class="toolbar-actions">
          <el-button :icon="Download" @click="openAgentGuide">下载 Agent</el-button>
          <el-button type="primary" :icon="Plus" @click="handleAdd">添加服务器</el-button>
        </span>
      </footer>
    </section>

    <ServerDialog
      v-model="addDialogVisible"
      :groups="groups"
      :server="editingServer"
      @success="onDialogClose"
    />

    <DebugDialog
      v-model="debugDialogVisible"
      :server-id="currentDebugServerId"
    />

    <TerminalDialog
      v-model="terminalDialogVisible"
      :server-id="currentTerminalServerId"
      :server="currentTerminalServer"
    />

    <AgentInstallGuide
      v-model="agentGuideVisible"
      @goto-add="onGuideGotoAdd"
    />
  </div>
</template>

<style scoped>
.server-panel {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  min-width: 0;
}

.server-toolbar {
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

.server-search {
  width: 200px;
  margin-left: auto;
}

@media (max-width: 860px) {
  .server-search {
    margin-left: 0;
    width: 100%;
  }
}
</style>
