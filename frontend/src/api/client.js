const BASE_OK = true

async function request(url, options = {}) {
  const headers = { ...(options.headers || {}) }
  const method = (options.method || 'GET').toUpperCase()
  let body

  if (options.body !== undefined && options.body !== null) {
    if (options.raw) {
      body = options.body
    } else {
      headers['Content-Type'] = 'application/json'
      body = JSON.stringify(options.body)
    }
  }

  let res
  try {
    res = await fetch(url, {
      method,
      headers,
      body,
      credentials: 'same-origin'
    })
  } catch (e) {
    return { success: false, message: '网络异常，请稍后重试' }
  }

  if (res.status === 401) {
    sessionStorage.removeItem('loginUser')
    const data = await res.json().catch(() => ({}))
    const kicked = data.message && String(data.message).includes('其他地方登录')
    window.location.href = kicked ? '/login?kicked=1' : '/login'
    return { success: false, message: '请先登录' }
  }

  try {
    return await res.json()
  } catch {
    return { success: false, message: '响应解析失败' }
  }
}

export const api = {
  get: (url) => request(url),
  post: (url, body) => request(url, { method: 'POST', body }),
  put: (url, body) => request(url, { method: 'PUT', body }),
  del: (url) => request(url, { method: 'DELETE' }),
  upload: (url, formData) =>
    request(url, { method: 'POST', body: formData, raw: true })
}

export default api

export function firstImage(product) {
  if (!product) return '/user-avatar/default.jpg'
  if (product.firstImage) return product.firstImage
  if (product.imageUrls && product.imageUrls.length) return product.imageUrls[0]
  if (product.imageUrl) return String(product.imageUrl).split(',')[0]
  return ''
}

export function formatPrice(v) {
  if (v === null || v === undefined || v === '') return '0.00'
  const n = Number(v)
  return Number.isFinite(n) ? n.toFixed(2) : String(v)
}

export function formatTime(v) {
  if (!v) return ''
  const d = new Date(v)
  if (Number.isNaN(d.getTime())) return String(v)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

export const CATEGORIES = [
  '全部',
  '手机',
  '笔记本电脑',
  '耳机',
  '相机',
  '手机壳',
  '空调',
  '冰箱',
  '半身裙',
  '其他'
]

export const ORDER_STATUS = {
  unshipped: { label: '待发货', tone: 'warning' },
  shipped: { label: '已发货', tone: 'info' },
  received: { label: '待评价', tone: 'info' },
  completed: { label: '已完成', tone: 'success' },
  cancelled: { label: '已取消', tone: 'muted' }
}

export const ORDER_FLOW = ['unshipped', 'shipped', 'received', 'completed']
