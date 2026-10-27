<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAppStore } from '../store/app'
import { Fold, Expand, Moon, Sunny } from '@element-plus/icons-vue'
import api from '../api/client'
import { authState, clearSession } from '../utils/session'

const store = useAppStore()
const route = useRoute()
const router = useRouter()

const pageTitle = computed(() => route.meta?.title || 'Re-Deploy')
const pageSubtitle = computed(() => route.meta?.subtitle || '')
const who = computed(() => authState.displayName || authState.username || '')

async function logout() {
  try {
    await api.logout()
  } catch {
    // 会话已经失效时直接清本地状态
  }
  clearSession()
  router.replace('/login')
}
</script>

<template>
  <el-header class="app-header">
    <div class="header-left">
      <el-button
        text
        circle
        :icon="store.sidebarCollapsed ? Expand : Fold"
        @click="store.toggleSidebar"
      />
      <div class="page-heading">
        <h1 class="page-title">{{ pageTitle }}</h1>
        <p v-if="pageSubtitle" class="page-subtitle">{{ pageSubtitle }}</p>
      </div>
    </div>
    <div class="header-right">
      <span v-if="who" class="who">{{ who }}</span>
      <button type="button" class="topbar-logout" @click="logout">退出</button>
      <el-button
        text
        :icon="store.isDark ? Sunny : Moon"
        @click="store.toggleTheme"
      >
        <span class="theme-label">{{ store.isDark ? '亮色' : '暗色' }}</span>
      </el-button>
    </div>
  </el-header>
</template>

<style scoped>
.app-header {
  --el-header-height: 56px;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 28px;
  background-color: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color);
}

.header-left {
  display: flex;
  align-items: center;
  gap: var(--spacing-4);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 4px;
}

.who {
  max-width: 110px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  color: var(--el-text-color-regular);
  white-space: nowrap;
}

.topbar-logout {
  height: 24px;
  padding: 0 8px;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  background: var(--el-bg-color);
  font-size: 11.5px;
  color: var(--el-text-color-regular);
  white-space: nowrap;
  cursor: pointer;
}

.topbar-logout:hover {
  background: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
}

.page-heading {
  min-width: 0;
}

.page-title {
  font-size: 15px;
  font-weight: 600;
  margin: 0;
  line-height: 1.2;
  color: var(--el-text-color-primary);
  letter-spacing: 0.01em;
}

.page-subtitle {
  margin: 1px 0 0;
  font-size: 11.5px;
  line-height: 1.3;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.theme-label {
  margin-left: var(--spacing-1);
}

@media (max-width: 768px) {
  .app-header {
    padding: 0 var(--spacing-4);
  }

  .theme-label {
    display: none;
  }
}
</style>
