<script setup>
import { onMounted, ref } from 'vue'
import api from '../api/client'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const saving = ref(false)
const concurrentDeployLimit = ref(2)

async function loadSettings() {
  loading.value = true
  try {
    const data = await api.getSystemSettings()
    concurrentDeployLimit.value = data.concurrentDeployLimit
  } catch (error) {
    ElMessage.error('加载系统设置失败')
    console.error(error)
  } finally {
    loading.value = false
  }
}

async function handleSave() {
  saving.value = true
  try {
    await api.updateSystemSettings({
      concurrentDeployLimit: concurrentDeployLimit.value
    })
    ElMessage.success('设置已保存')
    await loadSettings()
  } catch (error) {
    const msg = error?.response?.data?.error || '保存设置失败'
    ElMessage.error(msg)
    console.error(error)
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadSettings()
})
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="page-toolbar">
      <h2 class="page-title">系统设置</h2>
    </div>

    <el-card shadow="never" class="settings-card">
      <template #header>
        <span class="fw-medium">部署并发</span>
      </template>
      <el-form label-width="160px" @submit.prevent>
        <el-form-item label="同时部署并发数">
          <el-input-number
            v-model="concurrentDeployLimit"
            :min="1"
            :max="20"
            :step="1"
            step-strictly
          />
          <div class="form-tip">
            同时执行的部署任务数，默认 2；超出的部署将自动排队，完成一个后自动执行下一个。
          </div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.settings-card {
  max-width: 640px;
}

.form-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
  margin-top: 4px;
}
</style>
