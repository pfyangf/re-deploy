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

// 前置/后置命令支持的模板变量（与 DeployService.buildServerParams 注入项保持一致）
const templateVariables = [
  { name: '{{deploy.id}}', desc: '本次部署记录号，每次部署唯一', example: '456' },
  { name: '{{deploy.version}}', desc: '版本标识，未填时为空（Jenkins 任务默认 #构建号）', example: '#123' },
  { name: '{{deploy.time}}', desc: '部署开始时间', example: '20260917-094500' },
  { name: '{{deploy.timestamp}}', desc: '部署开始时间戳（秒）', example: '1758083100' },
  { name: '{{task.id}}', desc: '任务 ID，配置固定不变', example: '7' },
  { name: '{{task.name}}', desc: '任务名称', example: '网关部署' },
  { name: '{{server.name}}', desc: '目标服务器名称，按每台目标分别取值', example: '录像合成服务器-dat' },
  { name: '{{server.host}}', desc: '目标服务器 IP/主机名，按每台目标分别取值', example: '10.28.3.44' },
  { name: '{{jenkinsBuildNumber}}', desc: 'Jenkins 构建号，部署时填写', example: '123' },
  { name: '{{artifactFilename}}', desc: '制品文件名，Jenkins 任务自动注入', example: 'gateway-api.jar' }
]

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
    width="640"
    :close-on-click-modal="false"
    @close="handleClose"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <template #header>
      <div>
        <h2 class="od-dialog-title">{{ dialogTitle }}</h2>
        <p class="od-dialog-sub">编排可复用的部署或命令任务模板</p>
      </div>
    </template>
    <el-form class="od-form" :model="form" label-position="top">
      <div class="od-field-row">
        <el-form-item label="任务名称" required>
          <el-input v-model="form.name" maxlength="40" placeholder="请输入任务名称" />
        </el-form-item>
        <el-form-item label="任务类型" required>
          <el-select v-model="form.taskType" style="width: 100%">
            <el-option label="部署任务" value="deploy" />
            <el-option label="命令任务" value="command" />
          </el-select>
        </el-form-item>
      </div>
      <el-form-item label="所属分组" required>
        <el-select v-model="form.groupId" placeholder="请选择分组" style="width: 100%">
          <el-option v-for="g in groups" :key="g.id" :label="g.name" :value="g.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="任务描述">
        <el-input v-model="form.description" type="textarea" :rows="2" maxlength="200" placeholder="说明该任务的作用与执行时机" />
      </el-form-item>
      <el-form-item label="部署路径">
        <el-input v-model="form.deployPath" class="font-mono" placeholder="/opt/redeploy/releases" />
      </el-form-item>
      <el-form-item label="前置命令">
        <el-input v-model="form.beforeCommand" type="textarea" :rows="2" class="font-mono" placeholder="systemctl stop app" />
      </el-form-item>
      <el-form-item label="后置命令">
        <el-input v-model="form.afterCommand" type="textarea" :rows="2" class="font-mono" placeholder="systemctl start app" />
      </el-form-item>
      <el-collapse class="var-collapse">
        <el-collapse-item title="命令可用变量" name="vars">
          <p class="page-note">使用 <span v-pre>{{变量名}}</span>，部署时替换；未提供的变量保持原样。</p>
          <el-table :data="templateVariables" size="small" max-height="180">
            <el-table-column prop="name" label="变量" width="180">
              <template #default="{ row }"><code class="var-code">{{ row.name }}</code></template>
            </el-table-column>
            <el-table-column prop="desc" label="说明" min-width="160" />
            <el-table-column prop="example" label="示例" width="140" />
          </el-table>
        </el-collapse-item>
      </el-collapse>
      <div class="od-jenkins">
        <div class="od-jenkins-head">
          <el-switch v-model="form.jenkinsEnabled" />
          <span>Jenkins 集成</span>
          <span class="page-note">{{ form.jenkinsEnabled ? '已启用' : '未启用' }}</span>
        </div>
        <div v-if="form.jenkinsEnabled" class="od-jenkins-body">
          <el-form-item label="Jenkins 地址">
            <el-input v-model="form.jenkinsUrl" class="font-mono" placeholder="https://jenkins.internal" />
          </el-form-item>
          <el-form-item label="Job 名称">
            <el-input v-model="form.jenkinsJobName" class="font-mono" placeholder="group/job-name" />
          </el-form-item>
          <el-form-item label="构件路径">
            <el-input v-model="form.jenkinsArtifactPath" class="font-mono" placeholder="target/app.war" />
          </el-form-item>
          <div class="od-field-row">
            <el-form-item label="用户名">
              <el-input v-model="form.jenkinsUser" placeholder="jenkins-bot" />
            </el-form-item>
            <el-form-item label="API Token" style="margin-bottom: 0">
              <el-input v-model="form.jenkinsToken" type="password" show-password placeholder="下载权限令牌" />
            </el-form-item>
          </div>
        </div>
      </div>
    </el-form>
    <template #footer>
      <span class="od-dialog-note">Jenkins 令牌只保存在服务端</span>
      <div class="od-dialog-actions">
        <el-button @click="handleClose">取消</el-button>
        <el-button type="primary" :loading="loading" @click="handleSubmit">{{ submitButtonText }}</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.var-collapse {
  margin-bottom: 12px;
  border: 0;
}
.var-code {
  font-family: var(--el-font-family-mono);
  font-size: 12px;
  color: var(--el-color-primary);
}
</style>
