<script setup>
import { onBeforeUnmount, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '../api/client'
import { applyLogin, clientKey, firstMenuPath } from '../utils/session'

const route = useRoute()
const router = useRouter()

const mode = ref(localStorage.getItem('rd-login-tab') === 'password' ? 'password' : 'token')
const token = ref('')
const username = ref('')
const password = ref('')
const captcha = ref('')
const needCaptcha = ref(true)
const captchaUrl = ref('')
const loading = ref(false)
const errorText = ref('')

function switchMode(next) {
  mode.value = next
  localStorage.setItem('rd-login-tab', next)
  errorText.value = ''
  if (next === 'password') loadCaptchaState()
}

async function loadCaptchaState() {
  try {
    const status = await api.getCaptchaStatus(clientKey())
    needCaptcha.value = !!status.required
    if (needCaptcha.value) await refreshCaptcha()
  } catch (error) {
    needCaptcha.value = true
    errorText.value = error?.response?.data?.error || '无法连接服务器'
  }
}

async function refreshCaptcha() {
  const blob = await api.getCaptcha(clientKey())
  if (!(blob instanceof Blob) || (blob.type && blob.type.includes('json'))) {
    needCaptcha.value = true
    return
  }
  if (captchaUrl.value) URL.revokeObjectURL(captchaUrl.value)
  captchaUrl.value = URL.createObjectURL(blob)
}

function redirectTarget() {
  const raw = route.query.redirect
  const value = Array.isArray(raw) ? raw[0] : raw
  if (typeof value === 'string' && value.startsWith('/') && !value.startsWith('//') && !value.startsWith('/login')) {
    return value
  }
  return firstMenuPath()
}

async function submit() {
  errorText.value = ''
  loading.value = true
  try {
    const payload = mode.value === 'token'
      ? { type: 'token', token: token.value.trim() }
      : {
          type: 'password',
          username: username.value.trim(),
          password: password.value,
          captcha: captcha.value.trim(),
          clientKey: clientKey(),
        }
    const data = await api.login(payload)
    applyLogin(data)
    router.replace(redirectTarget())
  } catch (error) {
    const body = error?.response?.data || {}
    errorText.value = body.error || '登录失败'
    if (mode.value === 'password' && body.captchaRequired) {
      needCaptcha.value = true
      captcha.value = ''
      try { await refreshCaptcha() } catch { /* 图片失败时仍保留输入框 */ }
    }
  } finally {
    loading.value = false
  }
}

if (mode.value === 'password') loadCaptchaState()

onBeforeUnmount(() => {
  if (captchaUrl.value) URL.revokeObjectURL(captchaUrl.value)
})
</script>

<template>
  <div class="login-page">
    <form class="login-card" @submit.prevent="submit">
      <div class="brand">
        <span class="brand-mark">R</span>
        <span class="brand-text">
          <strong>Re-Deploy</strong>
          <small>内部部署平台</small>
        </span>
      </div>
      <div class="login-tabs" role="tablist" aria-label="登录方式">
        <button type="button" role="tab" :aria-selected="mode === 'token'" @click="switchMode('token')">密钥登录</button>
        <button type="button" role="tab" :aria-selected="mode === 'password'" @click="switchMode('password')">用户登录</button>
      </div>

      <div class="login-panel">
        <label v-if="mode === 'token'" class="field">
          <span>访问密钥 <i class="req">*</i></span>
          <el-input v-model="token" type="password" show-password placeholder="粘贴平台签发的访问密钥" />
        </label>
        <template v-else>
          <label class="field">
            <span>用户名 <i class="req">*</i></span>
            <el-input v-model="username" autocomplete="username" placeholder="请输入用户名" />
          </label>
          <label class="field">
            <span>密码 <i class="req">*</i></span>
            <el-input v-model="password" type="password" show-password autocomplete="current-password" placeholder="请输入密码" />
          </label>
          <div v-if="needCaptcha" class="field">
            <span>验证码 <i class="req">*</i></span>
            <div class="captcha-row">
              <el-input v-model="captcha" maxlength="4" placeholder="输入图中字符" />
              <button type="button" class="captcha-img" title="点击换一张" aria-label="验证码图片" @click="refreshCaptcha">
                <img v-if="captchaUrl" :src="captchaUrl" alt="验证码" />
                <span v-else>换一张</span>
              </button>
            </div>
            <p class="login-switch">
              <button type="button" class="link-btn" @click="refreshCaptcha">换一张</button>
            </p>
          </div>
        </template>
        <p v-if="errorText" class="login-error" role="alert">{{ errorText }}</p>
        <el-button type="primary" class="submit" native-type="submit" :loading="loading">登录</el-button>
      </div>
    </form>
    <div class="login-foot">
      <p>验证通过后 7 天内登录无需验证码。</p>
      <p>密码输入错误后需重新输入验证码。</p>
      <p>登录状态保留 3 天，超时需重新登录。</p>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: var(--el-bg-color-page);
  padding: 24px;
}

.login-card {
  width: 380px;
  max-width: 100%;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  box-shadow: 0 18px 44px rgba(12, 18, 24, 0.12);
}

.brand {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 22px 24px 14px;
}

.brand-text strong,
.brand-text small {
  display: block;
}

.brand-text strong {
  font-size: 14.5px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.brand-text small {
  margin-top: 1px;
  color: var(--el-text-color-secondary);
  font-size: 10.5px;
}

.brand-mark {
  width: 30px;
  height: 30px;
  flex: 0 0 30px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #151a20;
  color: #fff;
  font-size: 14px;
  font-weight: 600;
}

.login-tabs {
  display: grid;
  grid-template-columns: 1fr 1fr;
  margin: 0 18px;
  border: 1px solid var(--el-border-color);
  border-radius: 5px;
  overflow: hidden;
  background: var(--el-fill-color-light);
}

.login-tabs button {
  height: 34px;
  border: 0;
  border-right: 1px solid var(--el-border-color);
  background: var(--el-fill-color-light);
  color: var(--el-text-color-regular);
  font-size: 12.5px;
  cursor: pointer;
}

.login-tabs button:last-child {
  border-right: 0;
}

.login-tabs button[aria-selected="true"] {
  background: var(--el-bg-color);
  color: var(--el-text-color-primary);
  font-weight: 600;
  box-shadow: inset 0 -2px 0 var(--el-text-color-primary);
}

.login-panel {
  display: grid;
  gap: 12px;
  padding: 18px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.req {
  color: #ae2a1f;
  font-style: normal;
}

.captcha-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 108px;
  gap: 8px;
  align-items: start;
}

.captcha-img {
  width: 108px;
  height: 32px;
  padding: 0;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  background: #12171d;
  cursor: pointer;
  overflow: hidden;
}

.captcha-img img {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
}

.captcha-img span {
  color: #c3ced8;
  font-size: 11px;
}

.login-switch {
  display: flex;
  justify-content: flex-end;
  margin: 0;
}

.link-btn {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--el-color-primary);
  font-size: 11px;
  cursor: pointer;
}

.login-error {
  margin: 0;
  color: #ae2a1f;
  font-size: 11px;
  line-height: 1.5;
}

.submit {
  width: 100%;
  height: 34px;
}

.login-foot {
  width: 380px;
  max-width: 100%;
  margin-top: 14px;
  display: grid;
  gap: 4px;
  text-align: center;
}

.login-foot p {
  margin: 0;
  font-size: 10.5px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}
</style>
