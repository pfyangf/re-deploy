<script setup>
import { ref, watch, onUnmounted, nextTick } from 'vue'
import { Terminal } from 'xterm'
import { FitAddon } from 'xterm-addon-fit'
import 'xterm/css/xterm.css'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  serverId: { type: Number, default: null }
})

const emit = defineEmits(['update:modelValue'])

let terminal = null
let fitAddon = null
let socket = null
const containerRef = ref(null)

watch(() => props.modelValue, async (visible) => {
  if (visible) {
    await nextTick()
    initTerminal()
  } else {
    cleanup()
  }
})

function initTerminal() {
  if (!containerRef.value || !props.serverId) return

  cleanup()

  terminal = new Terminal({
    theme: {
      background: '#000000',
      foreground: '#ffffff'
    },
    fontFamily: "'Fira Code', monospace",
    fontSize: 14,
    cursorBlink: true
  })
  fitAddon = new FitAddon()
  terminal.loadAddon(fitAddon)
  terminal.open(containerRef.value)
  fitAddon.fit()

  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const wsUrl = `${protocol}//${window.location.host}/ws/bastion/${props.serverId}`
  socket = new WebSocket(wsUrl)

  socket.onmessage = (event) => {
    if (event.data instanceof Blob) {
      const reader = new FileReader()
      reader.onload = () => {
        terminal.write(reader.result)
      }
      reader.readAsBinaryString(event.data)
    } else {
      terminal.write(event.data)
    }
  }

  socket.onclose = () => {
    if (terminal) {
      terminal.write('\r\n\r\n--- 连接已关闭 ---')
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

function cleanup() {
  window.removeEventListener('resize', handleResize)
  if (terminal) {
    terminal.dispose()
    terminal = null
  }
  if (socket) {
    socket.close()
    socket = null
  }
  if (containerRef.value) {
    containerRef.value.innerHTML = ''
  }
}

function handleClose() {
  cleanup()
  emit('update:modelValue', false)
}

onUnmounted(() => {
  cleanup()
})
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="SSH 终端"
    width="900"
    :close-on-click-modal="false"
    @close="handleClose"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div ref="containerRef" class="terminal-container"></div>

    <template #footer>
      <el-button @click="handleClose">关闭</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.terminal-container {
  background: #000;
  padding: var(--spacing-3);
  border-radius: var(--el-border-radius-base);
  min-height: 480px;
}
</style>
