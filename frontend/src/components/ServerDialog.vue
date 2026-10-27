<script setup>
import { ref, watch, computed } from 'vue'
import api from '../api/client'
import { ElMessage } from 'element-plus'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  groups: { type: Array, default: () => [] },
  // 传入服务器对象时为编辑模式；为 null 时为新增模式
  server: { type: Object, default: null }
})

const emit = defineEmits(['update:modelValue', 'success'])

const isEdit = computed(() => !!(props.server && props.server.id))

const defaultForm = () => ({
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
})

const form = ref(defaultForm())
const loading = ref(false)

watch(() => props.modelValue, (visible) => {
  if (visible) {
    if (isEdit.value && props.server) {
      // 编辑模式：回填非敏感字段；密码/私钥密文不下发，留空表示不修改
      form.value = {
        id: props.server.id,
        name: props.server.name || '',
        host: props.server.host || '',
        port: props.server.port || 9009,
        agentToken: props.server.agentToken || '',
        groupId: props.server.groupId || props.groups[0]?.id || '',
        description: props.server.description || '',
        sshUsername: props.server.sshUsername || '',
        sshPassword: '',
        sshPrivateKey: '',
        sshPort: props.server.sshPort || 22
      }
    } else {
      form.value = defaultForm()
    }
  }
})

const dialogTitle = computed(() => isEdit.value ? '编辑服务器' : '添加服务器')
const submitText = computed(() => isEdit.value ? '保存' : '添加')

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
    if (isEdit.value) {
      // 密码/私钥留空 → 后端保留原值；填写了新值才覆盖
      await api.updateServer(form.value.id, data)
      ElMessage.success('保存成功')
    } else {
      await api.createServer(data)
      ElMessage.success('添加成功')
    }
    emit('success')
    handleClose()
  } catch (error) {
    ElMessage.error(isEdit.value ? '保存失败' : '添加失败')
    console.error(error)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    width="640"
    :close-on-click-modal="false"
    @close="handleClose"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <template #header>
      <div>
        <h2 class="od-dialog-title">{{ dialogTitle }}</h2>
        <p class="od-dialog-sub">保存后用于 Agent 通信，SSH 信息仅用于终端</p>
      </div>
    </template>
    <el-form class="od-form" :model="form" label-position="top">
      <el-form-item label="名称" required>
        <el-input v-model="form.name" maxlength="24" placeholder="请输入服务器名称" />
      </el-form-item>
      <div class="od-field-row">
        <el-form-item label="主机" required>
          <el-input v-model="form.host" class="font-mono" placeholder="10.0.0.1" />
        </el-form-item>
        <el-form-item label="Agent 端口">
          <el-input-number v-model="form.port" :min="1" :max="65535" controls-position="right" style="width: 100%" />
        </el-form-item>
      </div>
      <div class="od-field-row">
        <el-form-item label="分组" required>
          <el-select v-model="form.groupId" placeholder="请选择分组" style="width: 100%">
            <el-option v-for="g in groups" :key="g.id" :label="g.name" :value="g.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="Agent Token" required>
          <el-input v-model="form.agentToken" class="font-mono" placeholder="Agent 首次启动时打印的令牌" />
        </el-form-item>
      </div>
      <el-form-item label="描述">
        <el-input v-model="form.description" placeholder="说明这台机器的用途" />
      </el-form-item>
      <div class="od-jenkins">
        <div class="od-jenkins-head">SSH 配置<span class="page-note">可选，用于浏览器终端</span></div>
        <div class="od-jenkins-body">
          <div class="od-field-row">
            <el-form-item label="SSH 用户名">
              <el-input v-model="form.sshUsername" placeholder="root" />
            </el-form-item>
            <el-form-item label="SSH 端口">
              <el-input-number v-model="form.sshPort" :min="1" :max="65535" controls-position="right" style="width: 100%" />
            </el-form-item>
          </div>
          <el-form-item label="SSH 密码">
            <el-input
              v-model="form.sshPassword"
              type="password"
              show-password
              :placeholder="isEdit ? (server.hasSshPassword ? '已配置，留空则不修改' : '未配置，留空则不修改') : '请输入 SSH 密码'"
            />
          </el-form-item>
          <el-form-item label="SSH 私钥" style="margin-bottom: 0">
            <el-input
              v-model="form.sshPrivateKey"
              type="textarea"
              :rows="3"
              class="font-mono"
              :placeholder="isEdit ? (server.hasSshPrivateKey ? '已配置，留空则不修改' : '未配置，留空则不修改') : '-----BEGIN OPENSSH PRIVATE KEY-----'"
            />
          </el-form-item>
        </div>
      </div>
    </el-form>
    <template #footer>
      <span class="od-dialog-note">密码和私钥留空时保留原值</span>
      <div class="od-dialog-actions">
        <el-button @click="handleClose">取消</el-button>
        <el-button type="primary" :loading="loading" @click="handleSubmit">{{ submitText }}</el-button>
      </div>
    </template>
  </el-dialog>
</template>
