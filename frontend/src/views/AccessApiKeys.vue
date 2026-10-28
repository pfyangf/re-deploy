<script setup>
import { computed, ref } from 'vue'
import api from '../api/client'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'

const users = ref([])
const keys = ref([])
const loading = ref(false)
const saving = ref(false)
const visible = ref(false)
const secretVisible = ref(false)
const keyword = ref('')
const form = ref(emptyForm())
const created = ref(null)
const statsLoading = ref(false)
const recentLoading = ref(false)
const unit = ref('day')
const span = ref(7)
const recentPage = ref(1)
const recentSize = ref(10)
const recentTotal = ref(0)
const stats = ref(emptyStats())

const spanOptions = computed(() => unit.value === 'hour'
  ? [{ value: 24, label: '近 24 小时' }, { value: 48, label: '近 48 小时' }, { value: 72, label: '近 72 小时' }]
  : [{ value: 7, label: '近 7 天' }, { value: 14, label: '近 14 天' }, { value: 30, label: '近 30 天' }])

const filtered = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  if (!q) return keys.value
  return keys.value.filter((item) => `${item.name} ${item.keyPrefix} ${item.ownerUsername || ''}`.toLowerCase().includes(q))
})

const envText = computed(() => {
  if (!created.value?.token) return ''
  const base = window.location.origin
  return `REDEPLOY_BASE_URL=${base}\nREDEPLOY_API_KEY=${created.value.token}`
})

function emptyForm() {
  return { name: '', expireDays: '', userId: null }
}

function emptyStats() {
  return { total: 0, success: 0, denied: 0, byUser: [], byKey: [], byFeature: [], timeline: [], recent: [] }
}

function ownerOption(user) {
  return user.displayName ? `${user.username}（${user.displayName}）` : user.username
}

function ownerText(row) {
  if (!row.ownerUsername) return '未绑定'
  return row.ownerDisplayName ? `${row.ownerUsername}（${row.ownerDisplayName}）` : row.ownerUsername
}

function formatTime(value) {
  return value || '—'
}

async function loadStats(recentOnly = false) {
  if (recentOnly) recentLoading.value = true
  else statsLoading.value = true
  try {
    const data = (await api.getApiKeyStats({
      unit: unit.value,
      span: span.value,
      page: recentPage.value,
      size: recentSize.value
    })) || emptyStats()
    const recent = data.recent || []
    if (data.recentTotal == null) {
      recentTotal.value = recent.length
      const start = (recentPage.value - 1) * recentSize.value
      data.recent = recent.slice(start, start + recentSize.value)
    } else {
      recentTotal.value = Number(data.recentTotal)
      if (data.recentPage) recentPage.value = Number(data.recentPage)
    }
    if (recentOnly) stats.value.recent = data.recent
    else stats.value = data
  } catch (error) {
    ElMessage.error(error?.response?.data?.error || '加载调用统计失败')
  } finally {
    statsLoading.value = false
    recentLoading.value = false
  }
}

function changeRecentPage(page) {
  recentPage.value = page
  loadStats(true)
}

function changeRecentSize(size) {
  recentSize.value = size
  recentPage.value = 1
  loadStats(true)
}

function changeUnit(next) {
  unit.value = next
  span.value = next === 'hour' ? 24 : 7
  loadStats()
}

function barWidth(item, rows) {
  const max = Math.max(1, ...rows.map((row) => Number(row.total) || 0))
  return `${Math.round(((Number(item.total) || 0) / max) * 100)}%`
}

function columnHeight(item) {
  const max = Math.max(1, ...stats.value.timeline.map((row) => Number(row.total) || 0))
  return `${Math.max(2, Math.round(((Number(item.total) || 0) / max) * 100))}%`
}

const successRate = computed(() => {
  const total = Number(stats.value.total) || 0
  if (!total) return '—'
  return `${Math.round(((Number(stats.value.success) || 0) / total) * 100)}%`
})

function statusClass(code) {
  const value = Number(code)
  if (value >= 200 && value < 300) return 'is-ok'
  if (value >= 400) return 'is-bad'
  return ''
}

function splitLabel(label) {
  const text = String(label || '')
  const index = text.indexOf(' / ')
  if (index < 0) return { primary: text, secondary: '' }
  return { primary: text.slice(0, index), secondary: text.slice(index + 3) }
}

async function load() {
  loading.value = true
  try {
    keys.value = (await api.getApiKeys()) || []
    users.value = (await api.getApiKeyOwners()) || []
  } catch (error) {
    ElMessage.error(error?.response?.data?.error || '加载密钥失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  form.value = emptyForm()
  visible.value = true
}

async function save() {
  saving.value = true
  try {
    const payload = { name: form.value.name.trim(), userId: form.value.userId }
    if (String(form.value.expireDays).trim() !== '') payload.expireDays = Number(form.value.expireDays)
    created.value = await api.createApiKey(payload)
    visible.value = false
    secretVisible.value = true
    await load()
  } catch (error) {
    ElMessage.error(error?.response?.data?.error || '创建失败')
  } finally {
    saving.value = false
  }
}

async function toggleEnabled(row) {
  saving.value = true
  try {
    await api.updateApiKey(row.id, { enabled: row.enabled ? 0 : 1 })
    ElMessage.success(row.enabled ? '已停用' : '已启用')
    await load()
  } catch (error) {
    ElMessage.error(error?.response?.data?.error || '更新状态失败')
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`确定删除密钥 ${row.name}？已发出的调用会立即失效。`, '删除确认', { type: 'warning' })
    await api.deleteApiKey(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error?.response?.data?.error || '删除失败')
  }
}

async function copy(text) {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制')
  } catch {
    ElMessage.error('复制失败，请手动选择文本')
  }
}

load()
loadStats()
</script>

<template>
  <div class="page-container">
    <div class="page-toolbar">
      <h2 class="page-title">API 密钥</h2>
      <el-button type="primary" :icon="Plus" @click="openCreate">新建密钥</el-button>
    </div>
    <p class="page-note skill-note">
      密钥只在创建时显示一次。Cursor 打开本仓库会加载 <span class="font-mono">.cursor/skills/redeploy</span>。
      其他项目使用时，把该目录复制到个人技能目录 <span class="font-mono">~/.cursor/skills/redeploy/</span>，密钥通过环境变量提供，不要写进 Skill。
    </p>
    <section class="console-panel">
      <div class="console-toolbar">
        <el-input v-model="keyword" class="console-search" clearable placeholder="搜索名称或前缀" style="margin-left: 0" />
        <span class="page-note">共 {{ keys.length }} 个密钥</span>
      </div>
      <el-table v-loading="loading" :data="filtered" empty-text="还没有 API 密钥">
        <el-table-column prop="name" label="名称" min-width="120" />
        <el-table-column label="所属用户" min-width="160">
          <template #default="{ row }">{{ ownerText(row) }}</template>
        </el-table-column>
        <el-table-column label="前缀" min-width="160">
          <template #default="{ row }"><span class="font-mono">{{ row.keyPrefix }}…</span></template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <span class="state-text" :class="row.enabled ? 'is-on' : 'is-off'"><i class="dot"></i>{{ row.enabled ? '启用' : '停用' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="过期时间" min-width="170">
          <template #default="{ row }">{{ row.expiresAt || '永不过期' }}</template>
        </el-table-column>
        <el-table-column label="最近使用" min-width="170">
          <template #default="{ row }">{{ row.lastUsedAt || '尚未使用' }}</template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="170">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" class-name="col-actions" align="right">
          <template #default="{ row }">
            <el-button text @click="toggleEnabled(row)">{{ row.enabled ? '停用' : '启用' }}</el-button>
            <el-button text type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section class="console-panel stats-panel" v-loading="statsLoading">
      <div class="console-toolbar">
        <h2 class="page-title">调用统计</h2>
        <div class="stats-filters">
          <el-radio-group :model-value="unit" @change="changeUnit">
            <el-radio-button value="hour">按小时</el-radio-button>
            <el-radio-button value="day">按天</el-radio-button>
          </el-radio-group>
          <el-select v-model="span" style="width: 140px" @change="loadStats">
            <el-option v-for="item in spanOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </div>
      </div>
      <p class="page-note skill-note">只统计 API 密钥的调用，包含被拒绝的请求。记录保留 30 天。</p>
      <div class="stat-cards">
        <div class="stat-card">
          <span>调用次数</span>
          <strong>{{ stats.total }}</strong>
          <em>当前时间范围内的全部请求</em>
        </div>
        <div class="stat-card is-ok">
          <span>成功</span>
          <strong>{{ stats.success }}</strong>
          <em>成功率 {{ successRate }}</em>
        </div>
        <div class="stat-card is-bad">
          <span>拒绝</span>
          <strong>{{ stats.denied }}</strong>
          <em>无权限、失效或被策略拦截</em>
        </div>
      </div>
      <div class="chart-grid">
        <article class="chart-card">
          <header>
            <h3>按用户</h3>
            <span>{{ stats.byUser.length }} 个</span>
          </header>
          <div v-if="!stats.byUser.length" class="chart-empty">这段时间没有调用</div>
          <div v-for="item in stats.byUser" :key="item.label" class="bar-row">
            <span class="bar-label" :title="item.label">{{ item.label }}</span>
            <div class="bar-track"><i :style="{ width: barWidth(item, stats.byUser) }"></i></div>
            <span class="bar-value">{{ item.total }}</span>
          </div>
        </article>
        <article class="chart-card">
          <header>
            <h3>按用户与密钥</h3>
            <span>{{ stats.byKey.length }} 个</span>
          </header>
          <div v-if="!stats.byKey.length" class="chart-empty">这段时间没有调用</div>
          <div v-for="item in stats.byKey" :key="item.label" class="bar-row is-split">
            <span class="bar-label" :title="item.label">
              <b>{{ splitLabel(item.label).primary }}</b>
              <small v-if="splitLabel(item.label).secondary">{{ splitLabel(item.label).secondary }}</small>
            </span>
            <div class="bar-track"><i class="is-key" :style="{ width: barWidth(item, stats.byKey) }"></i></div>
            <span class="bar-value">{{ item.total }}</span>
          </div>
        </article>
        <article class="chart-card">
          <header>
            <h3>按功能</h3>
            <span>{{ stats.byFeature.length }} 项</span>
          </header>
          <div v-if="!stats.byFeature.length" class="chart-empty">这段时间没有调用</div>
          <div v-for="item in stats.byFeature" :key="item.label" class="bar-row">
            <span class="bar-label" :title="item.label">{{ item.label }}</span>
            <div class="bar-track"><i class="is-feature" :style="{ width: barWidth(item, stats.byFeature) }"></i></div>
            <span class="bar-value">{{ item.total }}</span>
          </div>
        </article>
      </div>
      <article class="chart-card trend-card">
        <header>
          <h3>调用趋势</h3>
          <span>{{ unit === 'hour' ? '按小时' : '按天' }}</span>
        </header>
        <div v-if="!stats.timeline.length" class="chart-empty">这段时间没有调用</div>
        <div v-else class="trend" :class="{ 'is-dense': stats.timeline.length > 24 }">
          <div v-for="item in stats.timeline" :key="item.label" class="trend-col" :title="`${item.label}  ${item.total} 次`">
            <span class="trend-value">{{ item.total || '' }}</span>
            <i :class="{ 'is-zero': !Number(item.total) }" :style="{ height: columnHeight(item) }"></i>
            <span class="trend-label">{{ item.label }}</span>
          </div>
        </div>
      </article>
      <article class="chart-card">
        <header>
          <h3>最近调用</h3>
          <span>共 {{ recentTotal }} 条</span>
        </header>
        <el-table v-loading="recentLoading" :data="stats.recent" empty-text="还没有调用记录">
          <el-table-column prop="createdAt" label="时间" min-width="170" />
          <el-table-column prop="username" label="用户" min-width="120" />
          <el-table-column prop="keyName" label="密钥" min-width="120" />
          <el-table-column prop="feature" label="功能" width="110" />
          <el-table-column label="请求" min-width="240">
            <template #default="{ row }"><span class="font-mono">{{ row.method }} {{ row.path }}</span></template>
          </el-table-column>
          <el-table-column label="状态" width="88">
            <template #default="{ row }">
              <span class="status-pill" :class="statusClass(row.statusCode)">{{ row.statusCode }}</span>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="recentTotal > 0" class="recent-foot">
          <el-pagination
            :total="recentTotal"
            :page-size="recentSize"
            :current-page="recentPage"
            :page-sizes="[10, 20, 40]"
            layout="total, sizes, prev, pager, next"
            @current-change="changeRecentPage"
            @size-change="changeRecentSize"
          />
        </div>
      </article>
    </section>

    <el-dialog v-model="visible" width="460px">
      <template #header>
        <div>
          <h2 class="od-dialog-title">新建 API 密钥</h2>
          <p class="od-dialog-sub">供智能体调用查询、新增和部署，不能操作系统管理</p>
        </div>
      </template>
      <div class="od-form">
        <label class="field">
          <span>所属用户</span>
          <el-select v-model="form.userId" filterable placeholder="选择用户" style="width: 100%">
            <el-option v-for="user in users" :key="user.id" :label="ownerOption(user)" :value="user.id" />
          </el-select>
        </label>
        <label class="field">
          <span>名称</span>
          <el-input v-model="form.name" maxlength="64" placeholder="例如 Cursor 部署" />
        </label>
        <label class="field">
          <span>有效天数</span>
          <el-input v-model="form.expireDays" placeholder="留空表示永不过期" />
        </label>
      </div>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="secretVisible" width="520px" :close-on-click-modal="false">
      <template #header>
        <div>
          <h2 class="od-dialog-title">密钥已创建</h2>
          <p class="od-dialog-sub">关闭后无法再次查看完整密钥，请立即复制</p>
        </div>
      </template>
      <div class="od-form">
        <label class="field">
          <span>密钥</span>
          <el-input :model-value="created?.token || ''" readonly />
        </label>
        <label class="field">
          <span>环境变量</span>
          <el-input :model-value="envText" type="textarea" :rows="3" readonly />
        </label>
      </div>
      <template #footer>
        <el-button @click="copy(created?.token || '')">复制密钥</el-button>
        <el-button type="primary" @click="copy(envText)">复制环境变量</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.skill-note {
  margin: 0;
  line-height: 1.6;
  white-space: normal;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 14px;
  font-size: 13px;
}

.state-text {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 11.5px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}

.state-text .dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--el-text-color-placeholder);
}

.state-text.is-on {
  color: var(--el-text-color-regular);
}

.state-text.is-off {
  color: #8a4b45;
}

.stats-filters {
  display: flex;
  align-items: center;
  gap: 10px;
}

.stat-cards {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 14px;
}

.stat-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-height: 92px;
  padding: 14px 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  background:
    linear-gradient(180deg, var(--el-fill-color-blank), var(--el-fill-color-light));
  border-left: 3px solid var(--el-color-primary);
}

.stat-card.is-ok {
  border-left-color: #3d9a6a;
}

.stat-card.is-bad {
  border-left-color: #c45c52;
}

.stat-card span,
.stat-card em,
.chart-card header span {
  font-size: 12px;
  font-style: normal;
  color: var(--el-text-color-secondary);
}

.stat-card strong {
  font-size: 28px;
  line-height: 1.1;
  font-weight: 650;
  font-variant-numeric: tabular-nums;
}

.chart-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 12px;
}

.chart-card {
  min-width: 0;
  margin-top: 12px;
  padding: 14px 16px 8px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  background: var(--el-fill-color-blank);
}

.chart-grid .chart-card {
  margin-top: 0;
}

.chart-card header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 12px;
}

.chart-card h3 {
  margin: 0;
  font-size: 13px;
  font-weight: 650;
}

.bar-row {
  display: grid;
  grid-template-columns: minmax(72px, 34%) 1fr 32px;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  font-size: 12px;
}

.bar-label,
.bar-value {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bar-row.is-split .bar-label {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
}

.bar-row.is-split .bar-label b {
  overflow: hidden;
  font-weight: 600;
  text-overflow: ellipsis;
}

.bar-row.is-split .bar-label small {
  overflow: hidden;
  color: var(--el-text-color-secondary);
  text-overflow: ellipsis;
}

.bar-value {
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.bar-track {
  height: 8px;
  border-radius: 99px;
  background: var(--el-fill-color);
  overflow: hidden;
}

.bar-track i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--el-color-primary-light-3), var(--el-color-primary));
}

.bar-track i.is-key {
  background: linear-gradient(90deg, #7c6cf0, #4f46e5);
}

.bar-track i.is-feature {
  background: linear-gradient(90deg, #3d9a6a, #2f7d56);
}

.chart-empty {
  padding: 18px 0 22px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  text-align: center;
}

.trend {
  display: flex;
  align-items: flex-end;
  gap: 6px;
  height: 168px;
  overflow-x: auto;
  padding: 8px 2px 4px;
  border-top: 1px dashed var(--el-border-color-lighter);
}

.trend-col {
  display: flex;
  flex: 1 0 42px;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  height: 100%;
  min-width: 42px;
}

.trend.is-dense .trend-col {
  flex-basis: 22px;
  min-width: 22px;
}

.trend-col i {
  width: 72%;
  min-height: 2px;
  border-radius: 5px 5px 2px 2px;
  background: linear-gradient(180deg, var(--el-color-primary-light-3), var(--el-color-primary));
}

.trend-col i.is-zero {
  opacity: 0.35;
  background: var(--el-border-color);
}

.trend-value,
.trend-label {
  font-size: 10px;
  color: var(--el-text-color-secondary);
  font-variant-numeric: tabular-nums;
}

.trend-label {
  margin-top: 6px;
  max-height: 72px;
  writing-mode: vertical-rl;
  transform: rotate(180deg);
}

.status-pill {
  display: inline-flex;
  min-width: 42px;
  justify-content: center;
  padding: 1px 6px;
  border-radius: 99px;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  background: var(--el-fill-color);
}

.status-pill.is-ok {
  color: #246b48;
  background: rgba(61, 154, 106, 0.16);
}

.status-pill.is-bad {
  color: #8a3d36;
  background: rgba(196, 92, 82, 0.16);
}

.recent-foot {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--el-border-color-lighter);
}

@media (max-width: 1100px) {
  .chart-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .stat-cards {
    grid-template-columns: 1fr;
  }
}
</style>
