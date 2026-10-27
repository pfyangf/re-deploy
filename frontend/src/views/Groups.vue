<script setup>
import { ref, computed } from 'vue'
import api from '../api/client'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete } from '@element-plus/icons-vue'
import GroupDialog from '../components/GroupDialog.vue'

const groups = ref([])
const servers = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const editingGroup = ref(null)
const keyword = ref('')

const visibleGroups = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  if (!q) return groups.value
  return groups.value.filter(g => `${g.name} ${g.description || ''}`.toLowerCase().includes(q))
})

async function loadGroups() {
  loading.value = true
  try {
    const [data, serverData] = await Promise.all([
      api.getGroups(),
      api.getServers().catch(() => [])
    ])
    groups.value = data || []
    servers.value = serverData || []
  } catch (error) {
    ElMessage.error('加载分组失败')
    console.error(error)
  } finally {
    loading.value = false
  }
}

function serverCount(group) {
  return servers.value.filter(server => server.groupId === group.id).length
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
      <h2 class="page-title">分组列表</h2>
      <el-button type="primary" :icon="Plus" @click="handleAdd">新增分组</el-button>
    </div>

    <section class="console-panel">
      <div class="console-toolbar">
        <el-input v-model="keyword" class="console-search" clearable placeholder="搜索名称或描述" style="margin-left: 0" />
        <span class="page-note">共 {{ groups.length }} 个分组</span>
      </div>
      <el-table v-loading="loading" :data="visibleGroups" empty-text="未找到匹配的分组">
        <el-table-column prop="name" label="名称" min-width="160">
          <template #default="{ row }">
            <span class="fw-medium">{{ row.name }}</span>
            <span v-if="row.name === 'default'" class="od-tag">默认</span>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.description || '—' }}
          </template>
        </el-table-column>
        <el-table-column label="服务器数" width="120">
          <template #default="{ row }">
            <span class="font-mono">{{ serverCount(row) }} 台</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="168" class-name="col-actions" align="right">
          <template #default="{ row }">
            <el-button text type="primary" :icon="Edit" @click="handleEdit(row)">编辑</el-button>
            <el-button
              text
              type="danger"
              :icon="Delete"
              :disabled="row.name === 'default'"
              :title="row.name === 'default' ? '默认分组不可删除' : ''"
              @click="handleDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <GroupDialog
      v-model="dialogVisible"
      :group="editingGroup"
      @success="onDialogClose"
    />
  </div>
</template>
