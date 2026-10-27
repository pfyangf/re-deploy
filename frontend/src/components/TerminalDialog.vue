<script setup>
import { ref, watch, onUnmounted, nextTick, computed } from 'vue'
import { Terminal } from 'xterm'
import { FitAddon } from 'xterm-addon-fit'
import 'xterm/css/xterm.css'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  serverId: { type: Number, default: null },
  server: { type: Object, default: null }
})

const emit = defineEmits(['update:modelValue'])

let terminal = null
let fitAddon = null
let socket = null
const containerRef = ref(null)
const open = ref(false)

const target = computed(() => props.server || null)
const title = computed(() => target.value?.name || 'SSH 终端')
const subtitle = computed(() => {
  const server = target.value
  if (!server) return '正在连接'
  const group = server.groupName || '未分组'
  const state = server.status === 'online' ? '在线' : '离线'
  return `${server.host}:${server.port} · ${group} · ${state}`
})

watch(() => props.modelValue, async (visible) => {
  if (visible) {
    window.addEventListener('keydown', onKeydown)
    await nextTick()
    requestAnimationFrame(() => {
      open.value = true
      initTerminal()
    })
  } else {
    open.value = false
    window.removeEventListener('keydown', onKeydown)
    cleanup()
  }
})

function initTerminal() {
  if (!containerRef.value || !props.serverId) return
  cleanup(false)

  terminal = new Terminal({
    theme: {
      background: '#0f1419',
      foreground: '#c3ced8',
      cursor: '#eef2f6',
      selectionBackground: '#2a3948'
    },
    fontFamily: '"JetBrains Mono", Consolas, "Liberation Mono", monospace',
    fontSize: 13,
    lineHeight: 1.35,
    cursorBlink: true,
    scrollback: 2000
  })
  fitAddon = new FitAddon()
  terminal.loadAddon(fitAddon)
  terminal.open(containerRef.value)
  fitAddon.fit()
  terminal.focus()

  if (target.value && target.value.status !== 'online') {
    terminal.write('\u001B[33m目标当前离线，SSH 可能无法建立。\u001B[0m\r\n')
  }
  terminal.write('\u001B[90m会话仅用于排查，操作会记入审计日志。\u001B[0m\r\n')

  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const wsUrl = `${protocol}//${window.location.host}/ws/bastion/${props.serverId}`
  socket = new WebSocket(wsUrl)

  socket.onmessage = (event) => {
    if (event.data instanceof Blob) {
      const reader = new FileReader()
      reader.onload = () => {
        if (terminal) terminal.write(reader.result)
      }
      reader.readAsBinaryString(event.data)
    } else if (terminal) {
      terminal.write(event.data)
    }
  }

  socket.onclose = (event) => {
    if (terminal) {
      const reason = event && event.reason ? '：' + event.reason : ''
      terminal.write(`\r\n\u001B[33m--- 连接已关闭${reason} ---\u001B[0m`)
    }
  }

  socket.onerror = () => {
    if (terminal) {
      terminal.write('\r\n\u001B[31m--- WebSocket 连接错误，请确认服务正常且已登录 ---\u001B[0m')
    }
  }

  terminal.onData((data) => {
    if (socket && socket.readyState === WebSocket.OPEN) {
      socket.send(data)
    }
  })

  window.addEventListener('resize', handleResize)
}

function handleResize() {
  if (!fitAddon || !terminal) return
  fitAddon.fit()
  if (socket && socket.readyState === WebSocket.OPEN) {
    socket.send(`resize:${terminal.cols}:${terminal.rows}`)
  }
}

function cleanup(clearDom = true) {
  window.removeEventListener('resize', handleResize)
  if (terminal) {
    terminal.dispose()
    terminal = null
  }
  if (socket) {
    socket.close()
    socket = null
  }
  if (clearDom && containerRef.value) {
    containerRef.value.innerHTML = ''
  }
}

function handleClose() {
  cleanup()
  open.value = false
  emit('update:modelValue', false)
}

function onKeydown(event) {
  if (event.key === 'Escape' && props.modelValue) handleClose()
}

onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown)
  cleanup()
})
</script>

<template>
  <teleport to="body">
    <div v-if="modelValue" class="term-scrim" @click="handleClose"></div>
    <aside
      v-if="modelValue"
      class="term-drawer"
      :class="{ 'is-open': open }"
      role="dialog"
      aria-modal="true"
      aria-label="服务器终端"
    >
      <header class="term-head">
        <div class="term-title">
          <span class="term-name">{{ title }}</span>
          <span class="term-sub">{{ subtitle }}</span>
        </div>
        <button type="button" class="term-close" aria-label="关闭终端" @click="handleClose">×</button>
      </header>
      <div ref="containerRef" class="term-body"></div>
      <footer class="term-foot">SSH 会话 · Esc 关闭</footer>
    </aside>
  </teleport>
</template>

<style scoped>
.term-scrim {
  position: fixed;
  inset: 0;
  z-index: 3000;
  background: rgba(10, 14, 18, 0.42);
}

.term-drawer {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  z-index: 3001;
  width: min(760px, 94vw);
  display: flex;
  flex-direction: column;
  background: #12171d;
  color: #c3ced8;
  box-shadow: -18px 0 48px rgba(10, 14, 18, 0.28);
  transform: translateX(100%);
  transition: transform 0.2s ease;
}

.term-drawer.is-open {
  transform: none;
}

.term-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 13px 16px;
  border-bottom: 1px solid #232c36;
}

.term-title {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.term-name {
  font-family: "JetBrains Mono", Consolas, monospace;
  font-size: 13px;
  font-weight: 600;
  color: #eef2f6;
}

.term-sub {
  font-family: "JetBrains Mono", Consolas, monospace;
  font-size: 10.5px;
  color: #7d8995;
  overflow-wrap: anywhere;
}

.term-close {
  width: 30px;
  height: 30px;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: #93a0ac;
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
}

.term-close:hover {
  background: #1e262f;
  color: #eef2f6;
}

.term-body {
  flex: 1;
  min-height: 0;
  padding: 12px 14px;
  background: #0f1419;
}

.term-body :deep(.xterm) {
  height: 100%;
}

.term-foot {
  padding: 8px 16px;
  border-top: 1px solid #232c36;
  background: #0f1419;
  font-size: 10.5px;
  color: #7d8995;
}
</style>
