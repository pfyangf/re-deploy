<script setup>
import { useRoute } from 'vue-router'
import AppSidebar from './components/AppSidebar.vue'
import AppHeader from './components/AppHeader.vue'

const route = useRoute()
</script>

<template>
  <router-view v-if="route.meta.public" />
  <el-container v-else class="app-container">
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
  padding: 20px 28px 40px;
  background-color: var(--el-bg-color-page);
  overflow-y: auto;
}

@media (max-width: 768px) {
  .app-main {
    padding: var(--spacing-4);
  }
}
</style>
