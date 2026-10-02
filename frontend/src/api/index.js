import api from './client'

export const authApi = {
  login: (username, password) => api.post('/api/login', { username, password }),
  register: (payload) => api.post('/api/register', payload),
  logout: () => api.post('/api/logout'),
  session: () => api.get('/api/session')
}

export const productApi = {
  list: (params = {}) => {
    const q = new URLSearchParams()
    Object.entries(params).forEach(([k, v]) => {
      if (v !== undefined && v !== null && v !== '') q.set(k, v)
    })
    return api.get(`/api/products?${q.toString()}`)
  },
  detail: (id) => api.get(`/api/products/${id}`),
  search: (params = {}) => {
    const q = new URLSearchParams()
    Object.entries(params).forEach(([k, v]) => {
      if (v !== undefined && v !== null && v !== '') q.set(k, v)
    })
    return api.get(`/api/products/search?${q.toString()}`)
  },
  create: (payload) => api.post('/api/products', payload),
  update: (id, payload) => api.put(`/api/products/${id}`, payload),
  remove: (id) => api.del(`/api/products/${id}`),
  mine: () => api.get('/api/user/products'),
  comments: (id) => api.get(`/api/products/${id}/comments`),
  addComment: (id, content) => api.post(`/api/products/${id}/comments`, { content }),
  deleteComment: (id, commentId) => api.del(`/api/products/${id}/comments/${commentId}`)
}

export const cartApi = {
  list: () => api.get('/api/cart'),
  add: (productId) => api.post('/api/cart', { productId }),
  remove: (id) => api.del(`/api/cart/${id}`),
  clear: () => api.del('/api/cart'),
  count: () => api.get('/api/cart/count')
}

export const orderApi = {
  create: (payload) => api.post('/api/orders', payload),
  createBatch: (payload) => api.post('/api/orders/batch', payload),
  list: (status) => api.get(`/api/orders${status ? `?status=${encodeURIComponent(status)}` : ''}`),
  detail: (id) => api.get(`/api/orders/${id}`),
  update: (id, payload) => api.put(`/api/orders/${id}`, payload),
  remove: (id) => api.del(`/api/orders/${id}`),
  cancel: (id) => api.post(`/api/orders/${id}/cancel`),
  complete: (id) => api.post(`/api/orders/${id}/complete`),
  sellerList: (status) =>
    api.get(`/api/seller/orders${status ? `?status=${encodeURIComponent(status)}` : ''}`)
}

export const favoriteApi = {
  toggle: (productId) => api.post('/api/favorites/toggle', { productId }),
  check: (productId) => api.get(`/api/favorites/check/${productId}`),
  list: () => api.get('/api/favorites'),
  remove: (favoriteId) => api.del(`/api/favorites/${favoriteId}`)
}

export const chatApi = {
  findRoom: (productId) => api.post('/api/chat/room', { productId }),
  createRoom: (sellerUsername, productId) =>
    api.post('/api/chat/room/create', { sellerUsername, productId }),
  rooms: () => api.get('/api/chat/rooms'),
  send: (payload) => api.post('/api/chat/message', payload),
  messages: (roomId, sinceId) =>
    api.get(
      `/api/chat/messages/${roomId}${sinceId ? `?sinceId=${sinceId}` : ''}`
    ),
  deleteRoom: (roomId) => api.del(`/api/chat/room/${roomId}`),
  unread: () => api.get('/api/chat/unread')
}

export const userApi = {
  info: () => api.get('/api/user'),
  update: (payload) => api.put('/api/user', payload),
  uploadAvatar: (formData) => api.upload('/api/user/avatar', formData),
  addresses: () => api.get('/api/addresses'),
  addAddress: (payload) => api.post('/api/addresses', payload),
  updateAddress: (id, payload) => api.put(`/api/addresses/${id}`, payload),
  deleteAddress: (id) => api.del(`/api/addresses/${id}`)
}

export const uploadApi = {
  images: (image) => api.post('/api/upload-image', { image })
}

export const adminApi = {
  login: (username, password) => api.post('/api/admin/login', { username, password }),
  stats: () => api.get('/api/admin/stats'),
  products: () => api.get('/api/admin/products'),
  deleteProduct: (id) => api.del(`/api/admin/products/${id}`),
  users: () => api.get('/api/admin/users'),
  deleteUser: (id) => api.del(`/api/admin/users/${id}`)
}
