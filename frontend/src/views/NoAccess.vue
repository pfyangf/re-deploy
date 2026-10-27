<script setup>
import { useRouter } from 'vue-router'
import { Lock } from '@element-plus/icons-vue'
import api from '../api/client'
import { clearSession } from '../utils/session'

const router = useRouter()

async function relogin() {
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
  <div class="no-perm">
    <span class="no-perm-icon"><el-icon :size="26"><Lock /></el-icon></span>
    <strong>当前账号没有可访问的菜单。</strong>
    <p>请联系管理员为你的角色分配菜单权限后再试。</p>
    <el-button @click="relogin">重新登录</el-button>
  </div>
</template>

<style scoped>
.no-perm {
  min-height: 60vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  text-align: center;
  color: var(--el-text-color-secondary);
}

.no-perm-icon {
  width: 56px;
  height: 56px;
  margin-bottom: 4px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--el-fill-color);
  color: var(--el-text-color-secondary);
}

.no-perm strong {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-regular);
}

.no-perm p {
  margin: 0;
  font-size: 12px;
}
</style>
