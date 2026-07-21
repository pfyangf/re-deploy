<script setup>
import { useRoute } from 'vue-router'
import { useAppStore } from '../store/app'
import {
  Odometer, Folder, Monitor, List, Promotion, Clock, Files
} from '@element-plus/icons-vue'

const store = useAppStore()
const route = useRoute()

const menuItems = [
  { index: '/dashboard', title: '仪表盘', icon: Odometer },
  { index: '/groups', title: '分组管理', icon: Folder },
  { index: '/servers', title: '服务器管理', icon: Monitor },
  { index: '/tasks', title: '任务管理', icon: List },
  { index: '/deploy', title: '部署操作', icon: Promotion },
  { index: '/history', title: '部署历史', icon: Clock },
  { index: '/artifacts', title: '构建产物', icon: Files },
]
</script>

<template>
  <el-aside :width="store.sidebarCollapsed ? '64px' : '220px'" class="app-sidebar">
    <div class="sidebar-brand">
      <el-icon :size="24" color="var(--el-color-primary)"><Promotion /></el-icon>
      <span v-show="!store.sidebarCollapsed" class="brand-text font-mono">Re-Deploy</span>
    </div>
    <el-menu
      :default-active="route.path"
      :collapse="store.sidebarCollapsed"
      :router="true"
      class="sidebar-menu"
    >
      <el-menu-item v-for="item in menuItems" :key="item.index" :index="item.index">
        <el-icon><component :is="item.icon" /></el-icon>
        <template #title>{{ item.title }}</template>
      </el-menu-item>
    </el-menu>
  </el-aside>
</template>

<style scoped>
.app-sidebar {
  background-color: var(--el-bg-color);
  border-right: 1px solid var(--el-border-color);
  transition: width 0.3s ease;
  overflow: hidden;
}

.sidebar-brand {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-2);
  border-bottom: 1px solid var(--el-border-color);
  padding: 0 var(--spacing-4);
}

.brand-text {
  font-size: 1.25rem;
  font-weight: 600;
  letter-spacing: -0.02em;
  color: var(--el-text-color-primary);
  white-space: nowrap;
}

.sidebar-menu {
  border-right: none;
  padding: var(--spacing-2);
}

.sidebar-menu:not(.el-menu--collapse) {
  width: 100%;
}

.sidebar-menu .el-menu-item {
  border-radius: var(--el-border-radius-base);
  margin-bottom: var(--spacing-1);
}

.sidebar-menu .el-menu-item.is-active {
  background-color: var(--el-color-primary);
  color: white;
}

.sidebar-menu .el-menu-item.is-active:hover {
  background-color: var(--el-color-primary);
  color: white;
}

.sidebar-menu .el-menu-item:hover {
  background-color: var(--el-fill-color-light);
}
</style>
