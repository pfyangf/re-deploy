<script setup>
import { onMounted, ref, computed } from 'vue'
import api from '../api/client'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, View, Delete } from '@element-plus/icons-vue'
import TaskDialog from '../components/TaskDialog.vue'

const groups = ref([])
const tasks = ref([])
const loading = ref(false)
const groupFilter = ref('')
const dialogVisible = ref(false)

const filteredTasks = computed(() => {
  if (!groupFilter.value) return tasks.value
  return tasks.value.filter(t => t.groupId === groupFilter.value)
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

function getTaskTypeTag(type) {
  return type === 'deploy' ? 'primary' : 'success'
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
  dialogVisible.value = true
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
      <h2 class="page-title">任务管理</h2>
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
        <el-button type="primary" :icon="Plus" @click="handleAdd">
          创建任务
        </el-button>
      </div>
    </div>

    <el-card shadow="never" class="table-card">
      <el-table
        v-loading="loading"
        :data="filteredTasks"
        stripe
        empty-text="暂无数据"
      >
        <el-table-column prop="id" label="ID" width="70">
          <template #default="{ row }">
            <span class="font-mono">{{ row.id }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="名称" min-width="140" />
        <el-table-column label="类型" width="120">
          <template #default="{ row }">
            <el-tag :type="getTaskTypeTag(row.taskType)" effect="light">
              {{ getTaskTypeLabel(row.taskType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="分组" width="120">
          <template #default="{ row }">
            {{ getGroupName(row.groupId) }}
          </template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="160">
          <template #default="{ row }">
            <span class="text-sm">{{ new Date(row.createdAt).toLocaleString() }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" :icon="View">查看</el-button>
            <el-button text type="danger" :icon="Delete" @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <TaskDialog
      v-model="dialogVisible"
      :groups="groups"
      @success="onDialogClose"
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

.text-sm {
  font-size: var(--text-sm);
}
</style>
