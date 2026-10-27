<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '../store/app'
import { Fold, Expand, Moon, Sunny } from '@element-plus/icons-vue'

const store = useAppStore()
const route = useRoute()

const pageTitle = computed(() => route.meta?.title || 'Re-Deploy')
const pageSubtitle = computed(() => route.meta?.subtitle || '')
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
