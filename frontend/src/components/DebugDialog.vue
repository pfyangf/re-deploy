<script setup>
import { ref, watch } from 'vue'
import api from '../api/client'
import { ElMessage } from 'element-plus'
import { Promotion } from '@element-plus/icons-vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  serverId: { type: Number, default: null }
})

const emit = defineEmits(['update:modelValue'])

const command = ref('')
const output = ref('')
const loading = ref(false)

watch(() => props.modelValue, (visible) => {
  if (visible) {
    command.value = ''
    output.value = ''
  }
})

function handleClose() {
  emit('update:modelValue', false)
}

async function executeCommand() {
  if (!props.serverId || !command.value.trim()) return

  loading.value = true
  output.value = '$ ' + command.value + '\n'
  try {
    const result = await api.executeDebug(props.serverId, command.value.trim())
    if (result.output) {
      output.value += result.output
    }
    if (typeof result.exitCode !== 'undefined') {
      output.value += `\n\n[Exit code: ${result.exitCode}]`
    }
    if (result.error && !result.output) {
      output.value += `Error: ${result.error}`
    }
  } catch (error) {
    output.value += '执行失败: ' + (error.message || error)
    ElMessage.error('执行失败')
  } finally {
    loading.value = false
  }
}

function handleEnter() {
  executeCommand()
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="服务器调试"
    width="700"
    :close-on-click-modal="false"
    @close="handleClose"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="debug-container">
      <el-input
        v-model="command"
        placeholder="输入命令，例如 ls -la"
        @keyup.enter="handleEnter"
      >
        <template #prepend>
          <el-icon><Promotion /></el-icon>
        </template>
        <template #append>
          <el-button :loading="loading" @click="executeCommand">执行</el-button>
        </template>
      </el-input>

      <div class="output-label">输出结果</div>
      <pre class="output-area font-mono">{{ output || '等待命令执行...' }}</pre>
    </div>

    <template #footer>
      <el-button @click="handleClose">关闭</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.debug-container {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-3);
}

.output-label {
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
  font-weight: 500;
}

.output-area {
  margin: 0;
  padding: var(--spacing-4);
  background-color: #1e1e1e;
  color: #e0e0e0;
  border-radius: var(--el-border-radius-base);
  min-height: 240px;
  max-height: 400px;
  overflow-y: auto;
  font-size: var(--text-sm);
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
