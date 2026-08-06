const STORAGE_KEY = 'redeploy_deploy_history'
const MAX_RECORDS = 3

export function saveDeployRecord(record) {
  const data = loadAll()
  const taskKey = String(record.taskId)
  if (!data[taskKey]) data[taskKey] = []

  const list = data[taskKey]
  const sortedIds = [...record.serverIds].sort((a, b) => a - b)

  const existIdx = list.findIndex(item =>
    item.version === record.version &&
    item.jenkinsBuildNumber === (record.jenkinsBuildNumber || '') &&
    Array.isArray(item.serverIds) &&
    item.serverIds.length === sortedIds.length &&
    [...item.serverIds].sort((a, b) => a - b).every((v, i) => v === sortedIds[i])
  )

  const entry = {
    taskId: record.taskId,
    taskName: record.taskName || '',
    groupId: record.groupId || null,
    groupName: record.groupName || '',
    serverIds: [...record.serverIds],
    version: record.version || '',
    jenkinsBuildNumber: record.jenkinsBuildNumber || '',
    timestamp: Date.now()
  }

  if (existIdx >= 0) {
    list.splice(existIdx, 1)
  }
  list.unshift(entry)

  if (list.length > MAX_RECORDS) {
    data[taskKey] = list.slice(0, MAX_RECORDS)
  }

  persist(data)
}

export function getDeployHistory(taskId) {
  const data = loadAll()
  return data[String(taskId)] || []
}

export function getDeployHistoryByTaskIds(taskIds) {
  const data = loadAll()
  const merged = []
  taskIds.forEach(id => {
    const list = data[String(id)]
    if (list && list.length) merged.push(...list)
  })
  merged.sort((a, b) => b.timestamp - a.timestamp)
  return merged.slice(0, MAX_RECORDS)
}

function loadAll() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? JSON.parse(raw) : {}
  } catch (e) {
    console.error('读取部署历史失败', e)
    return {}
  }
}

function persist(data) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(data))
  } catch (e) {
    console.error('保存部署历史失败', e)
  }
}
