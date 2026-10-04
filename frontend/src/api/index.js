import api from './client'
import {
  isVideoFile,
  readFileAsDataUrl,
  validateChatMedia
} from '../composables/chatMedia'

export const authApi = {
  login: (username, password) => api.post('/api/login', { username, password }),
  register: (payload) => api.post('/api/register', payload),
  logout: () => api.post('/api/logout'),
  session: () => api.get('/api/session'),
  resetPassword: (payload) => api.post('/api/user/reset-password', payload),
  sendResetCode: (payload) => api.post('/api/user/reset-code', payload)
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
  deleteComment: (id, commentId) => api.del(`/api/products/${id}/comments/${commentId}`),
  reviews: (id) => api.get(`/api/products/${id}/reviews`),
  related: (id, limit = 8) => api.get(`/api/products/${id}/related?limit=${limit}`)
}

export const shopApi = {
  detail: (username) => api.get(`/api/sellers/${encodeURIComponent(username)}`),
  reviews: (username) => api.get(`/api/sellers/${encodeURIComponent(username)}/reviews`),
  hotWords: () => api.get('/api/search/hot-words')
}

export const categoryApi = {
  list: () => api.get('/api/categories'),
  adminList: () => api.get('/api/admin/categories'),
  create: (payload) => api.post('/api/admin/categories', payload),
  update: (id, payload) => api.put(`/api/admin/categories/${id}`, payload),
  remove: (id) => api.del(`/api/admin/categories/${id}`)
}

export const announcementApi = {
  list: () => api.get('/api/announcements'),
  adminList: () => api.get('/api/admin/announcements'),
  create: (payload) => api.post('/api/admin/announcements', payload),
  update: (id, payload) => api.put(`/api/admin/announcements/${id}`, payload),
  remove: (id) => api.del(`/api/admin/announcements/${id}`)
}

export const notificationApi = {
  list: () => api.get('/api/notifications'),
  unreadCount: () => api.get('/api/notifications/unread-count'),
  readAll: () => api.post('/api/notifications/read-all'),
  readOne: (id) => api.post(`/api/notifications/${id}/read`)
}

export const reportApi = {
  create: (payload) => api.post('/api/reports', payload)
}

export const supportApi = {
  openSession: () => api.post('/api/support/session'),
  faq: () => api.get('/api/support/faq'),
  /** 用户点击常见问题：后台自动回复预设答案 */
  ask: (payload) => api.post('/api/support/ask', payload),
  rooms: () => api.get('/api/admin/support/rooms'),
  replies: () => api.get('/api/admin/support/replies'),
  addReply: (payload) => api.post('/api/admin/support/replies', payload),
  updateReply: (id, payload) => api.put(`/api/admin/support/replies/${id}`, payload),
  deleteReply: (id) => api.del(`/api/admin/support/replies/${id}`),
  messages: (roomId, sinceId) =>
    api.get(`/api/admin/support/messages/${roomId}${sinceId ? `?sinceId=${sinceId}` : ''}`),
  send: (payload) => api.post('/api/admin/support/message', payload)
}

export const cartApi = {
  list: () => api.get('/api/cart'),
  add: (productId, quantity = 1) => api.post('/api/cart', { productId, quantity }),
  updateQuantity: (id, quantity) => api.put(`/api/cart/${id}/quantity`, { quantity }),
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
  pay: (id) => api.post(`/api/orders/${id}/pay`),
  tracking: (id) => api.get(`/api/orders/${id}/tracking`),
  complete: (id) => api.post(`/api/orders/${id}/complete`),
  review: (id, payload) => api.post(`/api/orders/${id}/review`, payload),
  /** payload: ratingGoods/ratingService/ratingDelivery/content/images/videos */
  payWithCoupon: (id, userCouponId) =>
    api.post(`/api/orders/${id}/pay`, userCouponId ? { userCouponId } : {}),
  sellerList: (status) =>
    api.get(`/api/seller/orders${status ? `?status=${encodeURIComponent(status)}` : ''}`),
  sellerStats: () => api.get('/api/seller/stats'),
  /** 售后：申请 → 卖家同意退货 → 买家寄回填单号 → 卖家确认收货退款 */
  applyAfterSale: (id, reason) => api.post(`/api/orders/${id}/after-sale`, { reason }),
  approveAfterSale: (id) => api.post(`/api/orders/${id}/after-sale/approve`),
  rejectAfterSale: (id) => api.post(`/api/orders/${id}/after-sale/reject`),
  cancelAfterSale: (id) => api.post(`/api/orders/${id}/after-sale/cancel`),
  submitReturnTracking: (id, trackingNumber) =>
    api.post(`/api/orders/${id}/return-tracking`, { trackingNumber }),
  confirmReturnReceived: (id) => api.post(`/api/orders/${id}/return-received`)
}

export const offerApi = {
  create: (payload) => api.post('/api/offers', payload),
  mine: () => api.get('/api/offers/mine'),
  received: () => api.get('/api/offers/received'),
  accept: (id) => api.post(`/api/offers/${id}/accept`),
  reject: (id) => api.post(`/api/offers/${id}/reject`),
  cancel: (id) => api.post(`/api/offers/${id}/cancel`)
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
  recharge: (amount) => api.post('/api/user/recharge', { amount }),
  wallet: () => api.get('/api/user/wallet'),
  deleteAccount: (password) => api.post('/api/user/delete-account', { password }),
  withdraw: (payload) => api.post('/api/user/withdraw', payload),
  withdrawList: () => api.get('/api/user/withdraw'),
  addresses: () => api.get('/api/addresses'),
  addAddress: (payload) => api.post('/api/addresses', payload),
  updateAddress: (id, payload) => api.put(`/api/addresses/${id}`, payload),
  deleteAddress: (id) => api.del(`/api/addresses/${id}`)
}

export const uploadApi = {
  images: (image) => api.post('/api/upload-image', { image }),
  /** 评价图/视频（base64 列表） */
  reviewMedia: (media) => api.post('/api/upload-review-media', { media }),
  /** 聊天媒体（图片 5MB / 视频 30MB），返回 urls */
  media: (dataUrl) => api.post('/api/upload-media', { media: dataUrl }),
  /**
   * 校验并上传聊天媒体，返回 { type: 'image'|'video', url }
   * 非法文件抛 Error（message 为用户可读文案）
   */
  async chatMedia(file) {
    const err = validateChatMedia(file)
    if (err) throw new Error(err)
    const type = isVideoFile(file) ? 'video' : 'image'
    const dataUrl = await readFileAsDataUrl(file)
    const d = await uploadApi.media(dataUrl)
    if (!d.success) throw new Error(d.message || '上传失败')
    const url = (d.urls && d.urls[0]) || d.imageUrl || (d.imageUrls && d.imageUrls[0])
    if (!url) throw new Error('上传失败')
    return { type, url }
  }
}

export const adminApi = {
  login: (username, password) => api.post('/api/admin/login', { username, password }),
  stats: () => api.get('/api/admin/stats'),
  products: () => api.get('/api/admin/products'),
  deleteProduct: (id) => api.del(`/api/admin/products/${id}`),
  offlineProduct: (id) => api.post(`/api/admin/products/${id}/offline`),
  onlineProduct: (id) => api.post(`/api/admin/products/${id}/online`),
  // 发布审核
  approveProduct: (id) => api.post(`/api/admin/products/${id}/approve`),
  rejectProduct: (id, reason) => api.post(`/api/admin/products/${id}/reject`, { reason }),
  users: () => api.get('/api/admin/users'),
  deleteUser: (id) => api.del(`/api/admin/users/${id}`),
  banUser: (id, banned) => api.post(`/api/admin/users/${id}/ban`, { banned }),
  // 回收站：删除的商品 / 注销的用户
  recycleProducts: () => api.get('/api/admin/recycle/products'),
  restoreProduct: (id) => api.post(`/api/admin/recycle/products/${id}/restore`),
  purgeProduct: (id) => api.del(`/api/admin/recycle/products/${id}`),
  recycleUsers: () => api.get('/api/admin/recycle/users'),
  restoreUser: (id) => api.post(`/api/admin/recycle/users/${id}/restore`),
  purgeUser: (id) => api.del(`/api/admin/recycle/users/${id}`),
  orders: () => api.get('/api/admin/orders'),
  forceCancelOrder: (id) => api.post(`/api/admin/orders/${id}/force-cancel`),
  withdraws: () => api.get('/api/admin/withdraws'),
  approveWithdraw: (id) => api.post(`/api/admin/withdraws/${id}/approve`),
  rejectWithdraw: (id, reason) => api.post(`/api/admin/withdraws/${id}/reject`, { reason }),
  reports: () => api.get('/api/admin/reports'),
  /** action: none 仅标记 / offline 下架商品 / delete 商品进回收站 */
  resolveReport: (id, status, action = 'none') =>
    api.put(`/api/admin/reports/${id}`, { status, action }),
  // 审计日志（AOP 落库）
  auditLogs: (page = 0, size = 50, operator = '') =>
    api.get(`/api/admin/audit-logs?page=${page}&size=${size}${operator ? `&operator=${encodeURIComponent(operator)}` : ''}`)
}

export const reviewApi = {
  reply: (id, reply) => api.post(`/api/reviews/${id}/reply`, { reply }),
  followUp: (id, content) => api.post(`/api/reviews/${id}/followup`, { content })
}

export const couponApi = {
  claimable: () => api.get('/api/coupons/claimable'),
  mine: () => api.get('/api/coupons/mine'),
  claim: (id) => api.post(`/api/coupons/${id}/claim`),
  adminList: () => api.get('/api/admin/coupons'),
  create: (payload) => api.post('/api/admin/coupons', payload),
  update: (id, payload) => api.put(`/api/admin/coupons/${id}`, payload),
  remove: (id) => api.del(`/api/admin/coupons/${id}`)
}

/** 数据导出（CSV，带会话 Cookie 直接触发浏览器下载） */
export const exportApi = {
  orders: (status = '') => `/api/admin/export/orders${status ? `?status=${encodeURIComponent(status)}` : ''}`,
  users: () => '/api/admin/export/users',
  wallet: () => '/api/admin/export/wallet'
}
