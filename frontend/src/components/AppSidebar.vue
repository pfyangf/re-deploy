<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '../store/app'
import * as Icons from '@element-plus/icons-vue'
import { authState } from '../utils/session'

const store = useAppStore()
const route = useRoute()

const groups = computed(() => {
  const menus = authState.menus || []
  const parents = menus
    .filter((menu) => menu.menuType === 'group')
    .sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
  return parents.map((parent) => ({
    label: parent.name,
    items: menus
      .filter((menu) => menu.menuType === 'menu' && menu.parentId === parent.id && menu.path)
      .sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
      .map((menu) => ({
        index: menu.path,
        title: menu.name,
        icon: Icons[menu.icon] || Icons.Menu,
      })),
  })).filter((group) => group.items.length)
})

function iconOf(icon) {
  return icon || Icons.Menu
}
</script>

<template>
  <el-aside
    :width="store.sidebarCollapsed ? '64px' : '232px'"
    class="app-sidebar"
    :class="{ 'is-collapsed': store.sidebarCollapsed }"
  >
    <div class="side-head">
      <div class="brand">
        <span class="brand-mark">R</span>
        <span v-show="!store.sidebarCollapsed" class="brand-text">
          <span class="brand-name">Re-Deploy</span>
          <span class="brand-sub">内部部署</span>
        </span>
      </div>
    </div>
    <nav class="side-nav" aria-label="功能导航">
      <div v-for="group in groups" :key="group.label" class="nav-group">
        <div v-show="!store.sidebarCollapsed" class="nav-group-label">{{ group.label }}</div>
        <router-link
          v-for="item in group.items"
          :key="item.index"
          :to="item.index"
          class="nav-item"
          :class="{ 'is-active': route.path === item.index }"
          :aria-current="route.path === item.index ? 'page' : undefined"
          :title="store.sidebarCollapsed ? item.title : undefined"
        >
          <el-icon :size="16"><component :is="iconOf(item.icon)" /></el-icon>
          <span v-show="!store.sidebarCollapsed" class="nav-label">{{ item.title }}</span>
        </router-link>
      </div>
    </nav>
  </el-aside>
</template>

<style scoped>
.app-sidebar {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #151a20;
  color: #96a1ad;
  border-right: 1px solid #0d1115;
  transition: width 0.2s ease;
  overflow: hidden;
}

.side-head {
  display: flex;
  align-items: center;
  height: 56px;
  padding: 0 14px;
  flex: 0 0 56px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.brand-mark {
  width: 26px;
  height: 26px;
  flex: 0 0 26px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.07);
  border: 1px solid rgba(255, 255, 255, 0.16);
  color: #fff;
  font-weight: 600;
  font-size: 13px;
}

.brand-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.brand-name {
  color: #f1f4f7;
  font-size: 13.5px;
  font-weight: 600;
  letter-spacing: 0.01em;
  white-space: nowrap;
}

.brand-sub {
  color: #8b96a2;
  font-size: 10.5px;
  letter-spacing: 0.06em;
  white-space: nowrap;
}

.side-nav {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 4px 8px 14px;
}

.nav-group-label {
  font-size: 10.5px;
  letter-spacing: 0.1em;
  color: #8b96a2;
  padding: 0 10px;
  margin: 14px 0 6px;
  white-space: nowrap;
}

.nav-group:first-child .nav-group-label {
  margin-top: 6px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  height: 34px;
  padding: 0 10px;
  margin-bottom: 2px;
  border-radius: 4px;
  color: #96a1ad;
  font-size: 13px;
  text-decoration: none;
  box-sizing: border-box;
}

.nav-item:hover {
  background: #1d242c;
  color: #e6eaee;
}

.nav-item.is-active {
  background: #232b34;
  color: #ffffff;
  box-shadow: inset 2px 0 0 rgba(255, 255, 255, 0.72);
}

.nav-label {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.app-sidebar.is-collapsed .side-head {
  justify-content: center;
  padding: 0;
}

.app-sidebar.is-collapsed .side-nav {
  padding: 8px 6px;
}

.app-sidebar.is-collapsed .nav-item {
  justify-content: center;
  padding: 0;
}
</style>
