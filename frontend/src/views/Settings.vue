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
      <span class="page-note">平台级参数 · 保存后即时生效</span>
    </div>

    <div class="settings-grid">
      <section class="console-panel">
        <header class="console-toolbar">
          <strong>并发控制</strong>
        </header>
        <div class="settings-body">
          <label class="settings-field">
            <span>同时部署并发数</span>
            <el-input-number
              v-model="concurrentDeployLimit"
              :min="1"
              :max="20"
              :step="1"
              step-strictly
            />
          </label>
          <p class="console-hint">取值范围 1–20，默认 2。超过上限的部署任务进入等待队列。</p>
        </div>
        <footer class="panel-foot">
          <span>当前并发数 {{ concurrentDeployLimit }}</span>
          <el-button type="primary" :loading="saving" @click="handleSave">保存设置</el-button>
        </footer>
      </section>
      <aside class="console-panel">
        <header class="console-toolbar">
          <strong>执行策略</strong>
        </header>
        <div class="settings-body">
          <dl class="settings-kv">
            <div><dt>排队规则</dt><dd>超过并发上限的部署按提交顺序依次执行，先提交的任务优先获得执行权。</dd></div>
            <div><dt>并发上限</dt><dd class="font-mono">{{ concurrentDeployLimit }} 台</dd></div>
            <div><dt>生效范围</dt><dd>所有分组与单机部署共享同一个并发池</dd></div>
          </dl>
        </div>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.settings-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 12px;
  font-size: 12.5px;
}

.settings-kv div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px solid var(--el-border-color);
  font-size: 12.5px;
}

.settings-kv dt {
  color: var(--el-text-color-secondary);
}

.settings-kv dd {
  margin: 0;
}
</style>
