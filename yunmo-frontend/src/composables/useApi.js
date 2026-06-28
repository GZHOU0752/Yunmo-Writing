const BASE = '/api/v1'

function authHeaders() {
  const token = localStorage.getItem('yunmo_token')
  return token ? { Authorization: `Bearer ${token}` } : {}
}

export function getToken() { return localStorage.getItem('yunmo_token') }
export function setToken(t) { localStorage.setItem('yunmo_token', t) }
export function clearToken() { localStorage.removeItem('yunmo_token') }
export function isLoggedIn() { return !!localStorage.getItem('yunmo_token') }

async function request(url, options) {
  // 防御性处理：若 url 已经带了 /api/v1 前缀则不重复拼接
  const cleanUrl = url.startsWith(BASE) ? url.substring(BASE.length) || '/' : url
  const res = await fetch(`${BASE}${cleanUrl}`, {
    headers: { 'Content-Type': 'application/json', ...authHeaders(), ...options?.headers },
    ...options,
  })
  if (!res.ok) {
    if (res.status === 401) {
      clearToken()
      window.location.hash = '#/login'
      throw new Error('登录已过期，请重新登录')
    }
    const err = await res.json().catch(() => ({ detail: res.statusText }))
    throw new Error(err.detail || '请求失败，请重试')
  }
  // 204 No Content 或空响应体不解析 JSON
  const text = await res.text()
  return text ? JSON.parse(text) : null
}

/** 文件下载辅助函数 */
async function downloadFile(url, filename) {
  const token = localStorage.getItem('yunmo_token')
  const res = await fetch(`${BASE}${url}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })
  if (!res.ok) throw new Error('下载失败，请重试')
  const blob = await res.blob()
  const a = document.createElement('a')
  const blobUrl = URL.createObjectURL(blob)
  a.href = blobUrl
  a.download = filename
  a.click()
  // 延迟释放 Blob URL，确保浏览器已完成下载
  setTimeout(() => URL.revokeObjectURL(blobUrl), 1000)
}

export function useApi() {
  return {
    /** 通用 GET/POST（用于尚未封装 namespace 的端点） */
    get: (url) => request(url),
    post: (url, data) => request(url, { method: 'POST', body: JSON.stringify(data) }),
    /** 监控面板 */
    monitor: {
      stats: (novelId) => request(`/novels/${novelId}/monitor/stats`),
      foreshadows: (novelId) => request(`/novels/${novelId}/monitor/foreshadows`),
      audits: (novelId) => request(`/novels/${novelId}/monitor/audits`),
      characterGraph: (novelId) => request(`/novels/${novelId}/monitor/characters/graph`),
    },
    novels: {
      list: () => request('/novels'),
      get: (id) => request(`/novels/${id}`),
      create: (data) => request('/novels', { method: 'POST', body: JSON.stringify(data) }),
      update: (id, data) => request(`/novels/${id}`, { method: 'PATCH', body: JSON.stringify(data) }),
      delete: (id) => request(`/novels/${id}`, { method: 'DELETE' }),
      generateOutline: (id) => request(`/novels/${id}/generate-outline`, { method: 'POST' }),
      search: (id, keyword) => request(`/novels/${id}/search`, { method: 'POST', body: JSON.stringify({ keyword }) }),
      replace: (id, find, replace, chapterNumbers) => request(`/novels/${id}/replace`, { method: 'POST', body: JSON.stringify({ find, replace, chapterNumbers }) }),
      chat: (novelId, message, chapterNumber, history) => {
        const token = localStorage.getItem('yunmo_token')
        return fetch(`${BASE}/novels/${novelId}/chat`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
          body: JSON.stringify({ message, chapterNumber, history }),
        })
      },
    },
    characters: {
      list: (novelId) => request(`/novels/${novelId}/characters`),
      create: (novelId, data) => request(`/novels/${novelId}/characters`, { method: 'POST', body: JSON.stringify(data) }),
      update: (novelId, id, data) => request(`/novels/${novelId}/characters/${id}`, { method: 'PATCH', body: JSON.stringify(data) }),
      delete: (novelId, id) => request(`/novels/${novelId}/characters/${id}`, { method: 'DELETE' }),
      analyze: (novelId, id) => request(`/novels/${novelId}/characters/${id}/analyze`, { method: 'POST' }),
    },
    chapters: {
      list: (novelId) => request(`/novels/${novelId}/chapters`),
      get: (novelId, cn) => request(`/novels/${novelId}/chapters/${cn}`),
      create: (novelId, chapterNumber) => request(`/novels/${novelId}/chapters`, {
        method: 'POST',
        body: JSON.stringify(chapterNumber != null ? { chapterNumber } : {}),
      }),
      update: (novelId, cn, data) => request(`/novels/${novelId}/chapters/${cn}`, { method: 'PATCH', body: JSON.stringify(data) }),
      delete: (novelId, cn) => request(`/novels/${novelId}/chapters/${cn}`, { method: 'DELETE' }),
      versions: (novelId, cn) => request(`/novels/${novelId}/chapters/${cn}/versions`),
      restore: (novelId, cn, versionId) => request(`/novels/${novelId}/chapters/${cn}/versions/${versionId}/restore`, { method: 'POST' }),
      fork: (novelId, cn, branchName) => request(`/novels/${novelId}/chapters/${cn}/fork`, { method: 'POST', body: JSON.stringify({ branchName }) }),
      branches: (novelId, cn) => request(`/novels/${novelId}/chapters/${cn}/branches`),
      clearCheckpoint: (novelId, cn) => request(`/novels/${novelId}/chapters/${cn}/checkpoint`, { method: 'DELETE' }),
    },
    world: {
      list: (novelId) => request(`/novels/${novelId}/world`),
      create: (novelId, data) => request(`/novels/${novelId}/world`, { method: 'POST', body: JSON.stringify(data) }),
      delete: (novelId, id) => request(`/novels/${novelId}/world/${id}`, { method: 'DELETE' }),
    },
    genre: {
      list: () => request('/genre/list'),
    },
    stats: {
      overview: (novelId) => request(`/novels/${novelId}/stats`),
      history: (novelId, days) => request(`/novels/${novelId}/stats/history?days=${days || 30}`),
    },
    references: {
      list: (novelId) => request(`/novels/${novelId}/references`),
      upload: (novelId, fileName, content) => request(`/novels/${novelId}/references`, { method: 'POST', body: JSON.stringify({ fileName, content }) }),
      delete: (novelId, materialId) => request(`/novels/${novelId}/references/${materialId}`, { method: 'DELETE' }),
      updateTrigger: (novelId, materialId, data) => request(`/novels/${novelId}/references/${materialId}`, { method: 'PATCH', body: JSON.stringify(data) }),
    },
    outline: {
      list: (novelId) => request(`/novels/${novelId}/outline`),
      create: (novelId, data) => request(`/novels/${novelId}/outline`, { method: 'POST', body: JSON.stringify(data) }),
      update: (novelId, id, data) => request(`/novels/${novelId}/outline/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
      delete: (novelId, id) => request(`/novels/${novelId}/outline/${id}`, { method: 'DELETE' }),
      bindChapter: (novelId, id, chapterNumber) => request(`/novels/${novelId}/outline/${id}/bind-chapter`, { method: 'PUT', body: JSON.stringify({ chapterNumber }) }),
      discuss: (novelId, message, nodeId) => {
        const token = localStorage.getItem('yunmo_token')
        return fetch(`${BASE}/novels/${novelId}/outline/discuss`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
          body: JSON.stringify({ message, nodeId }),
        })
      },
      planChapter: (novelId, chapterNumber, answers) => {
        const token = localStorage.getItem('yunmo_token')
        return fetch(`${BASE}/novels/${novelId}/outline/plan-chapter/${chapterNumber}`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
          body: JSON.stringify({ answers }),
        })
      },
    },
    auth: {
      login: (email, password) => request('/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) }),
      register: (email, password, displayName) => request('/auth/register', { method: 'POST', body: JSON.stringify({ email, password, display_name: displayName }) }),
      me: () => request('/auth/me'),
      logout: () => request('/auth/logout', { method: 'POST' }),
      deleteAccount: () => request('/auth/account', { method: 'DELETE' }),
    },
    export: {
      epub: (novelId) => downloadFile(`/novels/${novelId}/export/epub`, 'novel.epub'),
      txt: (novelId) => downloadFile(`/novels/${novelId}/export/txt`, 'novel.txt'),
    },
    import: {
      preview: (novelId, text) => request(`/import/to-novel/${novelId}/preview`, { method: 'POST', body: JSON.stringify({ text }) }),
      execute: (novelId, text) => request(`/import/to-novel/${novelId}`, { method: 'POST', body: JSON.stringify({ text }) }),
      analyze: (novelId) => request(`/import/analyze/${novelId}`, { method: 'POST' }),
    },
  }
}
