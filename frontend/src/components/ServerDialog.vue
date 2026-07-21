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
  host: '',
  port: 9009,
  agentToken: '',
  groupId: '',
  description: '',
  sshUsername: '',
  sshPassword: '',
  sshPrivateKey: '',
  sshPort: 22
})

const loading = ref(false)

watch(() => props.modelValue, (visible) => {
  if (visible) {
    form.value = {
      name: '',
      host: '',
      port: 9009,
      agentToken: '',
      groupId: props.groups[0]?.id || '',
      description: '',
      sshUsername: '',
      sshPassword: '',
      sshPrivateKey: '',
      sshPort: 22
    }
  }
})

function handleClose() {
  emit('update:modelValue', false)
}

async function handleSubmit() {
  if (!form.value.name || !form.value.host || !form.value.agentToken) {
    ElMessage.warning('请填写必填字段')
    return
  }

  loading.value = true
  try {
    const data = {
      ...form.value,
      port: parseInt(form.value.port),
      sshPort: parseInt(form.value.sshPort),
      groupId: parseInt(form.value.groupId)
    }
    await api.createServer(data)
    ElMessage.success('添加成功')
    emit('success')
    handleClose()
  } catch (error) {
    ElMessage.error('添加失败')
    console.error(error)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="添加服务器"
    width="700"
    @close="handleClose"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form :model="form" label-width="120px" label-position="right">
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="服务器名称" required>
            <el-input v-model="form.name" placeholder="请输入服务器名称" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="主机地址" required>
            <el-input v-model="form.host" placeholder="请输入主机地址" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="Agent 端口">
            <el-input-number v-model="form.port" :min="1" :max="65535" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Agent Token" required>
            <el-input v-model="form.agentToken" placeholder="请输入 Agent Token" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="12">
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
        </el-col>
        <el-col :span="12">
          <el-form-item label="描述">
            <el-input v-model="form.description" placeholder="请输入描述" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-divider content-position="left">SSH 配置 (可选)</el-divider>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="SSH 用户名">
            <el-input v-model="form.sshUsername" placeholder="root" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="SSH 端口">
            <el-input-number v-model="form.sshPort" :min="1" :max="65535" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="SSH 密码">
            <el-input v-model="form.sshPassword" type="password" show-password />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="SSH 私钥">
            <el-input
              v-model="form.sshPrivateKey"
              type="textarea"
              :rows="3"
              placeholder="-----BEGIN RSA PRIVATE KEY-----..."
            />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleSubmit">
        添加
      </el-button>
    </template>
  </el-dialog>
</template>
