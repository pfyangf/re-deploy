<script setup>
import { ref, watch } from 'vue'
import api from '../api/client'
import { ElMessage } from 'element-plus'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  groups: { type: Array, default: () => [] }
})

const emit = defineEmits(['update:modelValue', 'success'])

const form = ref({
  name: '',
  groupId: '',
  taskType: 'deploy',
  deployPath: '/opt/application',
  beforeCommand: '',
  afterCommand: '',
  description: ''
})

const loading = ref(false)

watch(() => props.modelValue, (visible) => {
  if (visible) {
    form.value = {
      name: '',
      groupId: props.groups[0]?.id || '',
      taskType: 'deploy',
      deployPath: '/opt/application',
      beforeCommand: '',
      afterCommand: '',
      description: ''
    }
  }
})

function handleClose() {
  emit('update:modelValue', false)
}

async function handleSubmit() {
  if (!form.value.name || !form.value.groupId) {
    ElMessage.warning('请填写必填字段')
    return
  }

  loading.value = true
  try {
    const data = {
      ...form.value,
      groupId: parseInt(form.value.groupId)
    }
    await api.createTask(data)
    ElMessage.success('创建成功')
    emit('success')
    handleClose()
  } catch (error) {
    ElMessage.error('创建失败')
    console.error(error)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="创建任务"
    width="600"
    @close="handleClose"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form :model="form" label-width="100px" label-position="right">
      <el-form-item label="任务名称" required>
        <el-input v-model="form.name" placeholder="请输入任务名称" />
      </el-form-item>

      <el-form-item label="分组" required>
        <el-select v-model="form.groupId" placeholder="请选择分组" style="width: 100%">
          <el-option
            v-for="g in groups"
            :key="g.id"
            :label="g.name"
            :value="g.id"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="任务类型" required>
        <el-radio-group v-model="form.taskType">
          <el-radio value="deploy">部署任务</el-radio>
          <el-radio value="command">命令任务</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item label="部署路径">
        <el-input v-model="form.deployPath" placeholder="/opt/application" />
      </el-form-item>

      <el-form-item label="前置命令">
        <el-input
          v-model="form.beforeCommand"
          type="textarea"
          :rows="2"
          placeholder="部署前执行的命令"
        />
      </el-form-item>

      <el-form-item label="后置命令">
        <el-input
          v-model="form.afterCommand"
          type="textarea"
          :rows="2"
          placeholder="部署后执行的命令"
        />
      </el-form-item>

      <el-form-item label="描述">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="2"
          placeholder="请输入描述"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleSubmit">
        创建
      </el-button>
    </template>
  </el-dialog>
</template>
