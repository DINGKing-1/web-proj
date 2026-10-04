import { test, beforeEach, afterEach } from 'node:test'
import assert from 'node:assert/strict'

/**
 * 回归：匿名 401 不得把用户拽去登录页（否则打开首页会直接弹登录）。
 * 仅当本地仍有登录态（会话过期/被踢）时才跳转。
 */
function setupDom({ loginUser } = {}) {
  const store = new Map()
  if (loginUser) store.set('loginUser', loginUser)
  globalThis.sessionStorage = {
    getItem: (k) => (store.has(k) ? store.get(k) : null),
    setItem: (k, v) => store.set(k, String(v)),
    removeItem: (k) => store.delete(k)
  }
  const hrefs = []
  globalThis.window = {
    location: {
      pathname: '/',
      search: '',
      get href() {
        return hrefs[hrefs.length - 1] || '/'
      },
      set href(v) {
        hrefs.push(v)
      }
    },
    dispatchEvent: () => true
  }
  return { hrefs, store }
}

function mockFetch(status, body) {
  globalThis.fetch = async () => ({
    status,
    json: async () => body
  })
}

let saved

beforeEach(() => {
  saved = {
    fetch: globalThis.fetch,
    window: globalThis.window,
    sessionStorage: globalThis.sessionStorage
  }
})

afterEach(() => {
  globalThis.fetch = saved.fetch
  globalThis.window = saved.window
  globalThis.sessionStorage = saved.sessionStorage
})

test('anonymous 401 does not redirect to login', async () => {
  const { hrefs, store } = setupDom({ loginUser: null })
  mockFetch(401, { success: false, message: '请先登录' })
  const { api } = await import('./client.js')
  const d = await api.get('/api/announcements')
  assert.equal(d.unauthorized, true)
  assert.equal(hrefs.length, 0, '匿名 401 不应跳转登录页')
  assert.equal(store.has('loginUser'), false)
})

test('401 with cached login redirects to login with return path', async () => {
  const { hrefs } = setupDom({ loginUser: JSON.stringify({ username: 'u1' }) })
  mockFetch(401, { success: false, message: '请先登录' })
  globalThis.window.location.pathname = '/cart'
  const { api } = await import('./client.js')
  await api.get('/api/cart')
  assert.equal(hrefs.length, 1)
  assert.match(hrefs[0], /^\/login\?redirect=/)
})

test('kicked message always redirects even when anonymous', async () => {
  const { hrefs } = setupDom({ loginUser: null })
  mockFetch(401, { success: false, message: '您的账号已在其他地方登录' })
  const { api } = await import('./client.js')
  await api.get('/api/session')
  assert.equal(hrefs.length, 1)
  assert.match(hrefs[0], /kicked=1/)
})
