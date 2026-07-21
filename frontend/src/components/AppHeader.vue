<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '../store/app'
import { Fold, Expand, Moon, Sunny } from '@element-plus/icons-vue'

const store = useAppStore()
const route = useRoute()

const pageTitle = computed(() => route.meta?.title || 'Re-Deploy')
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
      <h1 class="page-title">{{ pageTitle }}</h1>
    </div>
    <div class="header-right">
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
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--spacing-6);
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
}

.page-title {
  font-size: 1.25rem;
  font-weight: 600;
  margin: 0;
  color: var(--el-text-color-primary);
  letter-spacing: -0.02em;
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
