<script setup>
import { onMounted, ref, computed } from 'vue'
import api from '../api/client'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, View, Edit, Delete, CopyDocument } from '@element-plus/icons-vue'
import TaskDialog from '../components/TaskDialog.vue'

const groups = ref([])
const tasks = ref([])
const loading = ref(false)
const groupFilter = ref('')
const keyword = ref('')
const createDialogVisible = ref(false)
const editDialogVisible = ref(false)
const copyDialogVisible = ref(false)
const viewDialogVisible = ref(false)
const currentTask = ref(null)

const filteredTasks = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  return tasks.value.filter(task => {
    if (groupFilter.value && task.groupId !== groupFilter.value) return false
    if (!q) return true
    return `${task.name} ${task.description || ''}`.toLowerCase().includes(q)
  })
})

function getGroupName(groupId) {
  if (!groups.value || !groupId) return '-'
  const g = groups.value.find(g => g.id === groupId)
  return g ? g.name : '-'
}

function getTaskTypeLabel(type) {
  const map = { deploy: '部署任务', command: '命令任务' }
  return map[type] || type
}

function getJenkinsEnabledLabel(enabled) {
  return enabled ? '是' : '否'
}

async function loadData() {
  loading.value = true
  try {
    const [groupsRes, tasksRes] = await Promise.all([
      api.getGroups(),
      api.getTasks()
    ])
    groups.value = groupsRes || []
    tasks.value = tasksRes || []
  } catch (error) {
    ElMessage.error('加载任务失败')
    console.error(error)
  } finally {
    loading.value = false
  }
}

function handleAdd() {
  createDialogVisible.value = true
}

function handleView(task) {
  currentTask.value = task
  viewDialogVisible.value = true
}

function handleEdit(task) {
  currentTask.value = task
  editDialogVisible.value = true
}

function handleCopy(task) {
  currentTask.value = task
  copyDialogVisible.value = true
}

async function handleDelete(task) {
  try {
    await ElMessageBox.confirm(
      `确定要删除任务「${task.name}」吗？`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
    await api.deleteTask(task.id)
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
      <h2 class="page-title">任务列表</h2>
      <el-button type="primary" :icon="Plus" @click="handleAdd">创建任务</el-button>
    </div>

    <section class="console-panel">
      <div class="console-toolbar">
        <el-select v-model="groupFilter" placeholder="全部分组" style="width: 160px">
          <el-option label="全部分组" value="" />
          <el-option v-for="group in groups" :key="group.id" :label="group.name" :value="group.id" />
        </el-select>
        <el-input v-model="keyword" class="console-search" clearable placeholder="搜索任务名称" />
      </div>
      <el-table v-loading="loading" :data="filteredTasks" empty-text="未找到匹配的任务">
        <el-table-column label="名称" min-width="180">
          <template #default="{ row }">
            <span class="fw-medium">{{ row.name }}</span>
            <span v-if="row.description" class="cell-sub">{{ row.description }}</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="120">
          <template #default="{ row }">
            {{ getTaskTypeLabel(row.taskType) }}
          </template>
        </el-table-column>
        <el-table-column label="分组" width="180">
          <template #default="{ row }">
            {{ getGroupName(row.groupId) }}
          </template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="160">
          <template #default="{ row }">
            <span class="text-sm">{{ new Date(row.createdAt).toLocaleString() }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="248" class-name="col-actions" align="right">
          <template #default="{ row }">
            <el-button text type="primary" :icon="View" @click="handleView(row)">查看</el-button>
            <el-button text type="primary" :icon="Edit" @click="handleEdit(row)">编辑</el-button>
            <el-button text type="primary" :icon="CopyDocument" @click="handleCopy(row)">复制</el-button>
            <el-button text type="danger" :icon="Delete" @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <footer class="panel-foot">任务模板在部署操作中可被选择复用</footer>
    </section>

    <!-- 创建任务对话框 -->
    <TaskDialog
      v-model="createDialogVisible"
      :groups="groups"
      @success="onDialogClose"
    />

    <!-- 编辑任务对话框 -->
    <TaskDialog
      v-model="editDialogVisible"
      :groups="groups"
      :task="currentTask"
      mode="edit"
      @success="onDialogClose"
    />

    <!-- 复制任务对话框 -->
    <TaskDialog
      v-model="copyDialogVisible"
      :groups="groups"
      :task="currentTask"
      mode="copy"
      @success="onDialogClose"
    />

    <!-- 查看详情只读对话框 -->
    <el-dialog v-model="viewDialogVisible" width="560">
      <template #header>
        <div>
          <h2 class="od-dialog-title">查看任务</h2>
          <p class="od-dialog-sub">只读查看任务编排内容</p>
        </div>
      </template>
      <dl class="task-detail">
        <div><dt>名称</dt><dd>{{ currentTask?.name || '—' }}</dd></div>
        <div><dt>类型</dt><dd>{{ getTaskTypeLabel(currentTask?.taskType) }}</dd></div>
        <div><dt>分组</dt><dd>{{ getGroupName(currentTask?.groupId) }}</dd></div>
        <div><dt>部署路径</dt><dd class="font-mono">{{ currentTask?.deployPath || '—' }}</dd></div>
        <div><dt>前置命令</dt><dd class="font-mono">{{ currentTask?.beforeCommand || '—' }}</dd></div>
        <div><dt>后置命令</dt><dd class="font-mono">{{ currentTask?.afterCommand || '—' }}</dd></div>
        <div><dt>Jenkins</dt><dd>{{ getJenkinsEnabledLabel(currentTask?.jenkinsEnabled) }}</dd></div>
        <template v-if="currentTask?.jenkinsEnabled">
          <div><dt>地址</dt><dd class="font-mono">{{ currentTask?.jenkinsUrl || '—' }}</dd></div>
          <div><dt>Job</dt><dd class="font-mono">{{ currentTask?.jenkinsJobName || '—' }}</dd></div>
          <div><dt>构件路径</dt><dd class="font-mono">{{ currentTask?.jenkinsArtifactPath || '—' }}</dd></div>
          <div><dt>用户名</dt><dd>{{ currentTask?.jenkinsUser || '—' }}</dd></div>
        </template>
        <div><dt>描述</dt><dd>{{ currentTask?.description || '—' }}</dd></div>
      </dl>
      <template #footer>
        <span class="od-dialog-note">创建于 {{ currentTask?.createdAt ? new Date(currentTask.createdAt).toLocaleString() : '—' }}</span>
        <div class="od-dialog-actions">
          <el-button @click="viewDialogVisible = false">关闭</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>
