/** 需要登录的页面前缀：会话过期时仅这些路径跳登录，公开页（首页等）只清状态 */
const AUTH_PATH_PREFIXES = [
  '/cart', '/checkout', '/orders', '/my-orders', '/order', '/user',
  '/favorites', '/notifications', '/support', '/messages', '/upload',
  '/seller-orders'
]

function isAuthPath(path) {
  if (AUTH_PATH_PREFIXES.some((p) => path === p || path.startsWith(p + '/') || path.startsWith(p + '?'))) {
    return true
  }
  // 管理端除登录页 /admin 外全部需要登录（覆盖 categories/audit/coupons 等新增页）
  return path === '/admin' ? false : path.startsWith('/admin')
}

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
      credentials: 'include'
    })
  } catch {
    // 网络故障 ≠ 登录失效：标记 networkError，调用方不得据此清会话
    return { success: false, networkError: true, message: '网络异常，请稍后重试' }
  }

  if (res.status === 401) {
    const data = await res.json().catch(() => ({}))
    const kicked = data.code === 'SESSION_KICKED'
      || (data.message && String(data.message).includes('其他地方登录'))
    // 仅当本地仍有登录态（会话过期/被踢）时才跳登录；
    // 匿名用户在公开页碰到 401（如未带凭据的探测）不应被拽去登录页
    const hadLogin = !!sessionStorage.getItem('loginUser')
    sessionStorage.removeItem('loginUser')
    window.dispatchEvent(new Event('auth-expired'))
    const path = window.location.pathname + window.location.search
    const onAuthPage = isAuthPath(window.location.pathname)
    // 公开页会话失效：留在原页（通常是首页），只在需要登录的操作时再跳
    if (kicked || (hadLogin && onAuthPage)) {
      const here = path
      const redirect = here && here !== '/login' ? `?redirect=${encodeURIComponent(here)}` : ''
      window.location.href = kicked
        ? `/login?kicked=1${redirect ? '&' + redirect.slice(1) : ''}`
        : `/login${redirect}`
    }
    return { success: false, unauthorized: true, code: data.code, message: data.message || '请先登录' }
  }

  let data
  try {
    data = await res.json()
  } catch {
    return { success: false, serverError: true, httpStatus: res.status, message: '响应解析失败' }
  }
  if (res.status >= 500) {
    // 服务端异常 ≠ 登录失效
    return { ...data, success: false, serverError: true, httpStatus: res.status }
  }
  return { ...data, httpStatus: res.status }
}

/** 认证失效（可据此登出/跳登录）。无管理员权限 ≠ 登录失效，不要清会话 */
export function isAuthFailure(d) {
  if (!d) return false
  if (d.unauthorized) return true
  if (d.success !== false || d.networkError || d.serverError) return false
  return /未登录|登录已|其他地方登录|请先登录/.test(String(d.message || ''))
}

/** 瞬时故障（网络/服务端异常）：静默重试，不要登出 */
export function isTransient(d) {
  return !!(d && (d.networkError || d.serverError))
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

/** 商品图统一占位（内联 SVG data URI，无网络请求、加载失败不产生 error 循环） */
const PLACEHOLDER_SVG = `<svg xmlns="http://www.w3.org/2000/svg" width="400" height="400" viewBox="0 0 400 400"><rect width="400" height="400" fill="#f3e9d7"/><g fill="none" stroke="#cdb388" stroke-width="10" stroke-linejoin="round"><rect x="110" y="130" width="180" height="140" rx="14"/><circle cx="158" cy="180" r="17"/><path d="M128 252l52-52 36 36 26-26 32 32"/></g><text x="200" y="318" text-anchor="middle" font-size="24" fill="#b49b6e" font-family="sans-serif">暂无图片</text></svg>`
export const PRODUCT_PLACEHOLDER = `data:image/svg+xml;charset=utf-8,${encodeURIComponent(PLACEHOLDER_SVG)}`

export function firstImage(product) {
  if (!product) return PRODUCT_PLACEHOLDER
  const raw =
    product.firstImage ||
    (product.imageUrls && product.imageUrls.length ? product.imageUrls[0] : '') ||
    product.imageUrl ||
    product.image ||
    ''
  if (!raw) return ''
  // 多图逗号分隔时只取第一张，避免 <img src="a.jpg,b.jpg"> 无法显示
  return String(raw).split(',')[0].trim()
}

/** 统一的商品图取值：无图时回退商品占位（勿用头像默认图） */
export function imgOf(x) {
  return firstImage(x) || PRODUCT_PLACEHOLDER
}

/** 商品图加载失败兜底（绑定 @error 用） */
export function onImgError(e) {
  const el = e?.target
  if (el && el.src !== PRODUCT_PLACEHOLDER) el.src = PRODUCT_PLACEHOLDER
}

export function formatPrice(v) {
  if (v === null || v === undefined || v === '') return '0.00'
  const n = Number(v)
  return Number.isFinite(n) ? n.toFixed(2) : String(v)
}

/** 金额转“分”（整数），用于无浮点误差的合计运算 */
export function toCents(v) {
  const n = Number(v)
  return Number.isFinite(n) ? Math.round(n * 100) : 0
}

/** 分 → “元”字符串（两位小数） */
export function formatCents(cents) {
  const n = Number(cents)
  return Number.isFinite(n) ? (n / 100).toFixed(2) : '0.00'
}

export function formatTime(v) {
  if (!v) return ''
  // Safari/iOS 不解析 "YYYY-MM-DD HH:mm:ss"，空格统一转 T
  const normalized = String(v).replace(' ', 'T')
  const d = new Date(normalized)
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
  pending_payment: { label: '待付款', tone: 'danger' },
  unshipped: { label: '待发货', tone: 'warning' },
  shipped: { label: '已发货', tone: 'info' },
  received: { label: '待评价', tone: 'info' },
  after_sale: { label: '售后中', tone: 'warning' },
  return_shipping: { label: '待寄回', tone: 'warning' },
  return_sent: { label: '退货已寄出', tone: 'info' },
  refunded: { label: '已退款', tone: 'muted' },
  completed: { label: '已完成', tone: 'success' },
  cancelled: { label: '已取消', tone: 'muted' }
}

/** 搜索历史 */
export function getSearchHistory() {
  try {
    return JSON.parse(localStorage.getItem('searchHistory') || '[]')
  } catch {
    return []
  }
}

export function pushSearchHistory(q) {
  if (!q || !q.trim()) return
  const key = q.trim()
  const list = getSearchHistory().filter((x) => x !== key)
  list.unshift(key)
  localStorage.setItem('searchHistory', JSON.stringify(list.slice(0, 10)))
}

export function clearSearchHistory() {
  localStorage.removeItem('searchHistory')
}

/** 浏览历史 */
export function getBrowseHistory() {
  try {
    return JSON.parse(localStorage.getItem('browseHistory') || '[]')
  } catch {
    return []
  }
}

export function pushBrowseHistory(item) {
  if (!item || !item.id) return
  const row = {
    id: item.id,
    name: item.name || '',
    image: firstImage(item) || PRODUCT_PLACEHOLDER,
    price: item.secondHandPrice,
    at: Date.now()
  }
  const list = getBrowseHistory().filter((x) => x.id !== row.id)
  list.unshift(row)
  localStorage.setItem('browseHistory', JSON.stringify(list.slice(0, 20)))
}

export function clearBrowseHistory() {
  localStorage.removeItem('browseHistory')
}

export const ORDER_FLOW = ['pending_payment', 'unshipped', 'shipped', 'received', 'completed']
