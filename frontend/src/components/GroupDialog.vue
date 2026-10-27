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
    width="460"
    :close-on-click-modal="false"
    @close="handleClose"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <template #header>
      <div>
        <h2 class="od-dialog-title">{{ group ? '编辑分组' : '新增分组' }}</h2>
        <p class="od-dialog-sub">分组用于组织服务器与批量部署</p>
      </div>
    </template>
    <el-form class="od-form" :model="form" label-position="top">
      <el-form-item label="名称" required>
        <el-input v-model="form.name" maxlength="24" placeholder="请输入分组名称" />
      </el-form-item>
      <el-form-item label="描述">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="3"
          maxlength="200"
          placeholder="说明该分组承载的业务与用途"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <span class="od-dialog-note">默认分组不可删除</span>
      <div class="od-dialog-actions">
        <el-button @click="handleClose">取消</el-button>
        <el-button type="primary" :loading="loading" @click="handleSubmit">保存</el-button>
      </div>
    </template>
  </el-dialog>
</template>
