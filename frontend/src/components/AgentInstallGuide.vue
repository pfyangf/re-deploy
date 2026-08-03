<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, Right } from '@element-plus/icons-vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'goto-add'])

const arch = ref('amd64')

const serverOrigin = typeof window !== 'undefined' ? window.location.origin : 'http://localhost:9006'

const downloadCommand = computed(() =>
  `sudo mkdir -p /opt/deploy-agent/{bin,conf,data,log} \\\n` +
  `&& sudo curl -fsSL "${serverOrigin}/api/agent/download/linux/${arch.value}" -o /opt/deploy-agent/bin/deploy-agent \\\n` +
  `&& sudo chmod +x /opt/deploy-agent/bin/deploy-agent`
)

const systemdCommand = `sudo tee /etc/systemd/system/deploy-agent.service <<'EOF'
[Unit]
Description=Deploy Agent Service
After=network.target

[Service]
Type=simple
User=root
ExecStart=/bin/bash -lc 'exec /opt/deploy-agent/bin/deploy-agent'
Restart=always
RestartSec=5
LimitNOFILE=65536
Environment=AGENT_CONFIG_DIR=/opt/deploy-agent/conf

[Install]
WantedBy=multi-user.target
EOF
&& sudo systemctl daemon-reload \\
&& sudo systemctl enable deploy-agent \\
&& sudo systemctl start deploy-agent`

const tokenCommand = `sudo grep token /opt/deploy-agent/conf/config.yaml`

const commonCommands = [
  { label: '查看状态', cmd: 'systemctl status deploy-agent' },
  { label: '查看日志', cmd: 'journalctl -u deploy-agent -f' },
  { label: '重启服务', cmd: 'sudo systemctl restart deploy-agent' },
  { label: '停止服务', cmd: 'sudo systemctl stop deploy-agent' },
  { label: '健康检查', cmd: 'curl http://127.0.0.1:9009/api/health' }
]

const copied = ref('')

async function copyText(text, key) {
  try {
    await navigator.clipboard.writeText(text)
    copied.value = key
    ElMessage.success('已复制到剪贴板')
    setTimeout(() => { copied.value = '' }, 2000)
  } catch {
    ElMessage.error('复制失败')
  }
}

function handleClose() {
  emit('update:modelValue', false)
}

function handleGotoAdd() {
  emit('goto-add')
  emit('update:modelValue', false)
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="Agent 安装指南"
    width="680"
    :close-on-click-modal="false"
    @close="handleClose"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="guide-content">
      <div class="step-section">
        <div class="step-title">
          <el-icon><Download /></el-icon>
          <span>第一步：下载并安装</span>
        </div>
        <p class="step-desc">在目标服务器上执行以下命令，自动下载并安装 Agent：</p>
        <div class="arch-selector">
          <el-radio-group v-model="arch" size="small">
            <el-radio-button value="amd64">Linux amd64</el-radio-button>
            <el-radio-button value="arm64">Linux arm64</el-radio-button>
          </el-radio-group>
        </div>
        <div class="code-block">
          <pre class="font-mono">{{ downloadCommand }}</pre>
          <el-button
            text
            type="primary"
            @click="copyText(downloadCommand, 'download')"
          >
            {{ copied === 'download' ? '已复制' : '复制' }}
          </el-button>
        </div>
      </div>

      <el-divider />

      <div class="step-section">
        <div class="step-title">
          <el-icon><Right /></el-icon>
          <span>第二步：配置 systemd 服务</span>
        </div>
        <p class="step-desc">创建 systemd 服务文件并启动 Agent：</p>
        <div class="code-block">
          <pre class="font-mono">{{ systemdCommand }}</pre>
          <el-button
            text
            type="primary"
            @click="copyText(systemdCommand, 'systemd')"
          >
            {{ copied === 'systemd' ? '已复制' : '复制' }}
          </el-button>
        </div>
      </div>

      <el-divider />

      <div class="step-section">
        <div class="step-title">
          <el-icon><Right /></el-icon>
          <span>第三步：获取 Token</span>
        </div>
        <p class="step-desc">首次启动后会自动生成 Token，执行以下命令查看：</p>
        <div class="code-block">
          <pre class="font-mono">{{ tokenCommand }}</pre>
          <el-button
            text
            type="primary"
            @click="copyText(tokenCommand, 'token')"
          >
            {{ copied === 'token' ? '已复制' : '复制' }}
          </el-button>
        </div>
        <p class="step-hint">将获取到的 Token 填入「添加服务器」表单即可。</p>
      </div>

      <el-divider />

      <div class="step-section">
        <div class="step-title">
          <el-icon><Right /></el-icon>
          <span>常用命令</span>
        </div>
        <div class="cmd-grid">
          <div v-for="cmd in commonCommands" :key="cmd.label" class="cmd-item">
            <span class="cmd-label">{{ cmd.label }}</span>
            <code class="font-mono cmd-code">{{ cmd.cmd }}</code>
          </div>
        </div>
      </div>

      <el-divider />

      <div class="step-section">
        <div class="step-title">
          <el-icon><Right /></el-icon>
          <span>目录结构</span>
        </div>
        <div class="dir-tree">
          <div class="dir-line"><span class="dir-name">/opt/deploy-agent/</span></div>
          <div class="dir-line indent-1"><span class="dir-name">bin/</span><span class="dir-desc">二进制文件</span></div>
          <div class="dir-line indent-2"><span class="file-name">deploy-agent</span></div>
          <div class="dir-line indent-1"><span class="dir-name">conf/</span><span class="dir-desc">配置文件（含 Token）</span></div>
          <div class="dir-line indent-2"><span class="file-name">config.yaml</span></div>
          <div class="dir-line indent-1"><span class="dir-name">data/</span><span class="dir-desc">数据目录（上传文件/脚本等）</span></div>
          <div class="dir-line indent-1"><span class="dir-name">log/</span><span class="dir-desc">运行日志（按天轮转，保留 30 天）</span></div>
        </div>
        <p class="step-hint">默认监听端口 9009，Token 首次启动自动生成。</p>
      </div>
    </div>

    <template #footer>
      <el-button @click="handleClose">关闭</el-button>
      <el-button type="primary" @click="handleGotoAdd">去添加服务器</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.guide-content {
  display: flex;
  flex-direction: column;
  gap: 0;
  max-height: 70vh;
  overflow-y: auto;
  padding-right: 4px;
}

.step-section {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-3);
}

.step-title {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
  font-weight: 600;
  font-size: 1rem;
  color: var(--el-text-color-primary);
}

.step-title .el-icon {
  color: var(--el-color-primary);
}

.step-desc {
  margin: 0;
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
}

.step-hint {
  margin: 0;
  font-size: var(--text-sm);
  color: var(--el-text-color-tertiary);
}

.arch-selector {
  margin-top: -4px;
}

.code-block {
  position: relative;
  background-color: #1e1e1e;
  border-radius: var(--el-border-radius-base);
  padding: var(--spacing-3) var(--spacing-4);
}

.code-block pre {
  margin: 0;
  color: #e0e0e0;
  font-size: var(--text-sm);
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  padding-right: 60px;
}

.code-block .el-button {
  position: absolute;
  top: var(--spacing-2);
  right: var(--spacing-2);
  color: #e0e0e0;
}

.code-block .el-button:hover {
  color: var(--el-color-primary-light-3);
}

.cmd-grid {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-2);
}

.cmd-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-3);
  font-size: var(--text-sm);
}

.cmd-label {
  min-width: 80px;
  color: var(--el-text-color-secondary);
}

.cmd-code {
  flex: 1;
  color: var(--el-text-color-primary);
  background-color: var(--el-fill-color-light);
  padding: 2px 8px;
  border-radius: 4px;
}

.dir-tree {
  font-family: var(--el-font-family);
  font-size: var(--text-sm);
  line-height: 1.8;
  color: var(--el-text-color-primary);
  background-color: var(--el-fill-color-light);
  border-radius: var(--el-border-radius-base);
  padding: var(--spacing-3) var(--spacing-4);
}

.dir-line {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
}

.indent-1 {
  padding-left: 20px;
}

.indent-2 {
  padding-left: 40px;
}

.dir-name {
  font-weight: 600;
  color: var(--el-color-primary);
}

.file-name {
  color: var(--el-text-color-secondary);
}

.dir-desc {
  color: var(--el-text-color-tertiary);
  font-size: calc(var(--text-sm) - 1px);
}
</style>
