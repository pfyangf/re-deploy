<script setup>
import { ref, watch } from 'vue'
import api from '../api/client'
import { ElMessage } from 'element-plus'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  group: { type: Object, default: null }
})

const emit = defineEmits(['update:modelValue', 'success'])

const form = ref({
  id: null,
  name: '',
  description: ''
})

const loading = ref(false)

// 监听 dialog 显示状态
watch(() => props.modelValue, (visible) => {
  if (visible) {
    if (props.group) {
      form.value = {
        id: props.group.id,
        name: props.group.name,
        description: props.group.description || ''
      }
    } else {
      form.value = { id: null, name: '', description: '' }
    }
  }
})

function handleClose() {
  emit('update:modelValue', false)
}

async function handleSubmit() {
  if (!form.value.name.trim()) {
    ElMessage.warning('请输入分组名称')
    return
  }

  loading.value = true
  try {
    const data = {
      name: form.value.name,
      description: form.value.description
    }
    if (form.value.id) {
      await api.updateGroup(form.value.id, data)
      ElMessage.success('更新成功')
    } else {
      await api.createGroup(data)
      ElMessage.success('创建成功')
    }
    emit('success')
    handleClose()
  } catch (error) {
    ElMessage.error('保存失败')
    console.error(error)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="group ? '编辑分组' : '新增分组'"
    width="500"
    @close="handleClose"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form :model="form" label-width="80px" label-position="right">
      <el-form-item label="名称" required>
        <el-input v-model="form.name" placeholder="请输入分组名称" />
      </el-form-item>
      <el-form-item label="描述">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="3"
          placeholder="请输入描述"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleSubmit">
        保存
      </el-button>
    </template>
  </el-dialog>
</template>
