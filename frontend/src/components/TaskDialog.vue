<script setup>
import { ref, watch, computed } from 'vue'
import api from '../api/client'
import { ElMessage } from 'element-plus'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  groups: { type: Array, default: () => [] },
  task: { type: Object, default: null }, // 编辑模式传入任务对象
  mode: { type: String, default: 'edit' } // 'edit' | 'copy'，仅在传入 task 时区分
})

const emit = defineEmits(['update:modelValue', 'success'])

const isCopy = computed(() => !!props.task && props.mode === 'copy')
const isEdit = computed(() => !!props.task && props.mode !== 'copy')

const form = ref({
  name: '',
  groupId: '',
  taskType: 'deploy',
  deployPath: '/opt/application',
  beforeCommand: '',
  afterCommand: '',
  description: '',
  jenkinsEnabled: false,
  jenkinsUrl: '',
  jenkinsJobName: '',
  jenkinsArtifactPath: '',
  jenkinsUser: '',
  jenkinsToken: ''
})

const loading = ref(false)

watch(() => props.modelValue, (visible) => {
  if (visible) {
    if (props.task) {
      // Edit / Copy mode - fill with existing data
      form.value = {
        name: isCopy.value ? `${props.task.name || ''} 副本` : (props.task.name || ''),
        groupId: props.task.groupId?.toString() || '',
        taskType: props.task.taskType || 'deploy',
        deployPath: props.task.deployPath || '/opt/application',
        beforeCommand: props.task.beforeCommand || '',
        afterCommand: props.task.afterCommand || '',
        description: props.task.description || '',
        jenkinsEnabled: props.task.jenkinsEnabled || false,
        jenkinsUrl: props.task.jenkinsUrl || '',
        jenkinsJobName: props.task.jenkinsJobName || '',
        jenkinsArtifactPath: props.task.jenkinsArtifactPath || '',
        jenkinsUser: props.task.jenkinsUser || '',
        jenkinsToken: props.task.jenkinsToken || ''
      }
    } else {
      // Create mode - reset to defaults
      form.value = {
        name: '',
        groupId: props.groups[0]?.id || '',
        taskType: 'deploy',
        deployPath: '/opt/application',
        beforeCommand: '',
        afterCommand: '',
        description: '',
        jenkinsEnabled: false,
        jenkinsUrl: '',
        jenkinsJobName: '',
        jenkinsArtifactPath: '',
        jenkinsUser: '',
        jenkinsToken: ''
      }
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
    if (isEdit.value) {
      // Edit mode
      await api.updateTask(props.task.id, data)
      ElMessage.success('更新成功')
    } else {
      // Create mode (or copy mode -> create new task from clone)
      await api.createTask(data)
      ElMessage.success(isCopy.value ? '复制成功' : '创建成功')
    }
    emit('success')
    handleClose()
  } catch (error) {
    ElMessage.error(isEdit.value ? '更新失败' : (isCopy.value ? '复制失败' : '创建失败'))
    console.error(error)
  } finally {
    loading.value = false
  }
}

const dialogTitle = computed(() => {
  if (isEdit.value) return '编辑任务'
  if (isCopy.value) return '复制任务'
  return '创建任务'
})

const submitButtonText = computed(() => {
  if (isEdit.value) return '保存'
  if (isCopy.value) return '复制'
  return '创建'
})
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="dialogTitle"
    width="600"
    :close-on-click-modal="false"
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

      <el-form-item label="Jenkins 集成">
        <el-checkbox v-model="form.jenkinsEnabled">启用 Jenkins 构建物下载</el-checkbox>
      </el-form-item>

      <template v-if="form.jenkinsEnabled">
        <el-form-item label="Jenkins 地址">
          <el-input v-model="form.jenkinsUrl" placeholder="http://jenkins:8080" />
        </el-form-item>

        <el-form-item label="Job 名称">
          <el-input v-model="form.jenkinsJobName" placeholder="job/group/job-name" />
          <div class="hint">完整路径，和 Jenkins URL 拼接后可访问</div>
        </el-form-item>

        <el-form-item label="构件路径">
          <el-input v-model="form.jenkinsArtifactPath" placeholder="project/target/app.war" />
        </el-form-item>

        <el-form-item label="用户名">
          <el-input v-model="form.jenkinsUser" placeholder="Jenkins 用户名（需要下载权限）" />
        </el-form-item>

        <el-form-item label="API Token">
          <el-input v-model="form.jenkinsToken" type="password" placeholder="Jenkins API Token" />
        </el-form-item>
      </template>

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
        {{ submitButtonText }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}
</style>
