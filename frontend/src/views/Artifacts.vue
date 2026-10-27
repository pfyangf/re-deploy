<script setup>
import { onMounted, ref, computed } from 'vue'
import api from '../api/client'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download, Delete } from '@element-plus/icons-vue'

const artifacts = ref([])
const loading = ref(false)
const keyword = ref('')

const visibleArtifacts = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  if (!q) return artifacts.value
  return artifacts.value.filter(item => `${item.filename} ${item.md5 || ''}`.toLowerCase().includes(q))
})

function formatSize(bytes) {
  if (!bytes) return '-'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0
  while (bytes >= 1024 && i < units.length - 1) {
    bytes /= 1024
    i++
  }
  return `${bytes.toFixed(2)} ${units[i]}`
}

async function loadArtifacts() {
  loading.value = true
  try {
    const data = await api.getArtifacts()
    artifacts.value = data || []
  } catch (error) {
    ElMessage.error('加载产物失败')
    console.error(error)
  } finally {
    loading.value = false
  }
}

async function handleDelete(artifact) {
  try {
    await ElMessageBox.confirm(
      `确定要删除构建产物「${artifact.filename}」吗？`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
    await api.deleteArtifact(artifact.id)
    ElMessage.success('删除成功')
    await loadArtifacts()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
      console.error(error)
    }
  }
}

function handleDownload(artifact) {
  window.open(api.downloadArtifact(artifact.id), '_blank')
}

onMounted(() => {
  loadArtifacts()
})
</script>

<template>
  <div class="page-container">
    <div class="page-toolbar">
      <h2 class="page-title">构建产物</h2>
      <el-button @click="loadArtifacts">刷新</el-button>
    </div>

    <section class="console-panel">
      <div class="console-toolbar">
        <el-input v-model="keyword" class="console-search" clearable placeholder="搜索文件名或 MD5" style="margin-left: 0" />
        <span class="page-note">共 {{ artifacts.length }} 个产物</span>
      </div>
      <el-table v-loading="loading" :data="visibleArtifacts" empty-text="未找到匹配的构建产物">
        <el-table-column prop="filename" label="文件名" min-width="200">
          <template #default="{ row }">
            <span class="fw-medium">{{ row.filename }}</span>
          </template>
        </el-table-column>
        <el-table-column label="大小" width="120">
          <template #default="{ row }">
            {{ formatSize(row.fileSize) }}
          </template>
        </el-table-column>
        <el-table-column label="MD5" min-width="200">
          <template #default="{ row }">
            <code class="font-mono md5-code">{{ row.md5 || '-' }}</code>
          </template>
        </el-table-column>
        <el-table-column label="上传时间" min-width="160">
          <template #default="{ row }">
            <span class="text-sm">{{ new Date(row.uploadedAt).toLocaleString() }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="148" class-name="col-actions" align="right">
          <template #default="{ row }">
            <el-button
              text
              type="primary"
              :icon="Download"
              @click="handleDownload(row)"
            >
              下载
            </el-button>
            <el-button
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
    </section>
  </div>
</template>

<style scoped>
.md5-code {
  padding: 2px 6px;
  background-color: var(--el-fill-color-light);
  border-radius: var(--el-border-radius-small);
  font-size: var(--text-xs);
  word-break: break-all;
}
</style>
