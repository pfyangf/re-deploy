<script setup>
import { ref } from 'vue'
import api from '../api/client'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete } from '@element-plus/icons-vue'
import GroupDialog from '../components/GroupDialog.vue'

const groups = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const editingGroup = ref(null)

async function loadGroups() {
  loading.value = true
  try {
    const data = await api.getGroups()
    groups.value = data || []
  } catch (error) {
    ElMessage.error('加载分组失败')
    console.error(error)
  } finally {
    loading.value = false
  }
}

function handleAdd() {
  editingGroup.value = null
  dialogVisible.value = true
}

function handleEdit(group) {
  editingGroup.value = { ...group }
  dialogVisible.value = true
}

async function handleDelete(group) {
  try {
    await ElMessageBox.confirm(
      '确定要删除此分组吗？分组下必须没有服务器才能删除。',
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
    await api.deleteGroup(group.id)
    ElMessage.success('删除成功')
    await loadGroups()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
      console.error(error)
    }
  }
}

function onDialogClose() {
  dialogVisible.value = false
  loadGroups()
}

loadGroups()
</script>

<template>
  <div class="page-container">
    <div class="page-toolbar">
      <h2 class="page-title">分组管理</h2>
      <el-button type="primary" :icon="Plus" @click="handleAdd">新增分组</el-button>
    </div>

    <el-card shadow="never" class="table-card">
      <el-table
        v-loading="loading"
        :data="groups"
        stripe
        empty-text="暂无数据"
      >
        <el-table-column prop="id" label="ID" width="80">
          <template #default="{ row }">
            <span class="font-mono">{{ row.id }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="名称" min-width="160">
          <template #default="{ row }">
            <span class="fw-medium">{{ row.name }}</span>
            <el-tag v-if="row.name === 'default'" type="info" size="small" class="ms-2">默认</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.description || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" :icon="Edit" @click="handleEdit(row)">编辑</el-button>
            <el-button
              v-if="row.name !== 'default'"
              text
              type="danger"
              :icon="Delete"
              @click="handleDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <GroupDialog
      v-model="dialogVisible"
      :group="editingGroup"
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

.fw-medium {
  font-weight: 500;
}

.ms-2 {
  margin-left: var(--spacing-2);
}
</style>
