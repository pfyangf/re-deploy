<script setup>
import { onMounted, ref } from 'vue'
import api from '../api/client'
import { Cpu, Monitor, TrendCharts, CircleCheck } from '@element-plus/icons-vue'

const stats = ref({
  totalServers: 0,
  onlineServers: 0,
  totalDeploys: 0,
  successRate: 0
})

const recentDeploys = ref([])
const servers = ref([])
const loading = ref(true)

async function loadData() {
  loading.value = true
  try {
    const [serversRes, historyRes] = await Promise.all([
      api.getServers(),
      api.getDeployHistory()
    ])

    if (serversRes) {
      servers.value = serversRes
      stats.value.totalServers = serversRes.length
      stats.value.onlineServers = serversRes.filter(s => s.status === 'online').length
    }

    if (historyRes) {
      const successCount = historyRes.filter(h => h.status === 'success').length
      stats.value.totalDeploys = historyRes.length
      stats.value.successRate = historyRes.length > 0
        ? Math.round((successCount / historyRes.length) * 100)
        : 0
      recentDeploys.value = historyRes.slice(0, 8)
    }
  } catch (error) {
    console.error('Failed to load dashboard data:', error)
  } finally {
    loading.value = false
  }
}

function getStatusType(status) {
  const map = { success: 'success', failed: 'danger', running: 'warning' }
  return map[status] || 'info'
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div v-loading="loading">
    <!-- 统计卡片 -->
    <el-row :gutter="20" class="stats-row">
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon stat-icon-blue">
              <el-icon :size="28"><Monitor /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-label font-mono">服务器总数</div>
              <div class="stat-value" style="color: var(--el-color-primary)">
                {{ stats.totalServers }}
              </div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon stat-icon-green">
              <el-icon :size="28"><CircleCheck /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-label font-mono">在线服务器</div>
              <div class="stat-value" style="color: var(--el-color-success)">
                {{ stats.onlineServers }}
              </div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon stat-icon-orange">
              <el-icon :size="28"><TrendCharts /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-label font-mono">总部署次数</div>
              <div class="stat-value" style="color: var(--el-color-warning)">
                {{ stats.totalDeploys }}
              </div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon stat-icon-purple">
              <el-icon :size="28"><Cpu /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-label font-mono">成功率</div>
              <div
                class="stat-value"
                :style="{ color: stats.successRate >= 90 ? 'var(--el-color-success)' : stats.successRate >= 70 ? 'var(--el-color-warning)' : 'var(--el-color-danger)' }"
              >
                {{ stats.successRate }}%
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 列表区域 -->
    <el-row :gutter="20" class="list-row">
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="list-card">
          <template #header>
            <div class="card-header-title">
              <el-icon><Monitor /></el-icon>
              <span>服务器状态</span>
            </div>
          </template>
          <el-table :data="servers" stripe size="default" empty-text="暂无数据">
            <el-table-column prop="name" label="名称" min-width="120" />
            <el-table-column label="地址" min-width="180">
              <template #default="{ row }">
                <span class="font-mono text-sm">{{ row.host }}:{{ row.port }}</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="120" align="center">
              <template #default="{ row }">
                <el-tag
                  :type="row.status === 'online' ? 'success' : 'danger'"
                  effect="light"
                  round
                >
                  {{ row.status }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="list-card">
          <template #header>
            <div class="card-header-title">
              <el-icon><Clock /></el-icon>
              <span>最近部署</span>
            </div>
          </template>
          <el-table :data="recentDeploys" stripe size="default" empty-text="暂无部署记录">
            <el-table-column prop="id" label="ID" width="80">
              <template #default="{ row }">
                <span class="font-mono text-sm">{{ row.id }}</span>
              </template>
            </el-table-column>
            <el-table-column label="版本" min-width="100">
              <template #default="{ row }">
                {{ row.version || '-' }}
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="getStatusType(row.status)" effect="light" round>
                  {{ row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="时间" min-width="160">
              <template #default="{ row }">
                <span class="text-sm">{{ new Date(row.createdAt).toLocaleString() }}</span>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.stats-row {
  margin-bottom: var(--spacing-5, 20px);
}

.stat-card {
  margin-bottom: var(--spacing-4);
  border-radius: var(--el-border-radius-round);
  border: 1px solid var(--el-border-color-light);
}

.stat-content {
  display: flex;
  align-items: center;
  gap: var(--spacing-4);
  padding: var(--spacing-4) 0;
}

.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-icon-blue {
  background-color: rgba(64, 158, 255, 0.1);
  color: var(--el-color-primary);
}

.stat-icon-green {
  background-color: rgba(103, 194, 58, 0.1);
  color: var(--el-color-success);
}

.stat-icon-orange {
  background-color: rgba(230, 162, 60, 0.1);
  color: var(--el-color-warning);
}

.stat-icon-purple {
  background-color: rgba(144, 147, 153, 0.1);
  color: var(--el-color-info);
}

.stat-info {
  flex: 1;
  min-width: 0;
}

.stat-label {
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
  margin-bottom: var(--spacing-1);
  font-weight: 500;
}

.stat-value {
  font-size: 2rem;
  font-weight: 700;
  line-height: 1.2;
  letter-spacing: -0.02em;
}

.list-row {
  margin-top: var(--spacing-4);
}

.list-card {
  margin-bottom: var(--spacing-4);
  border-radius: var(--el-border-radius-round);
}

.card-header-title {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.text-sm {
  font-size: var(--text-sm);
}
</style>
