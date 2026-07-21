<script setup>
import { useAppStore } from './store/app'
import AppSidebar from './components/AppSidebar.vue'
import AppHeader from './components/AppHeader.vue'

const store = useAppStore()
</script>

<template>
  <el-container class="app-container">
    <AppSidebar />
    <el-container direction="vertical">
      <AppHeader />
      <el-main class="app-main">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.app-container {
  height: 100vh;
}

.app-main {
  padding: var(--spacing-6);
  background-color: var(--el-bg-color-page);
  overflow-y: auto;
}

@media (max-width: 768px) {
  .app-main {
    padding: var(--spacing-4);
  }
}
</style>
