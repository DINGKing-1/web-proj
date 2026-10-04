import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  firstImage,
  imgOf,
  formatPrice,
  formatTime,
  toCents,
  formatCents,
  PRODUCT_PLACEHOLDER,
  CATEGORIES,
  ORDER_STATUS,
  ORDER_FLOW
} from './client.js'

test('firstImage prefers firstImage then imageUrls then imageUrl', () => {
  assert.equal(firstImage({ firstImage: '/a.jpg' }), '/a.jpg')
  assert.equal(firstImage({ imageUrls: ['/b.jpg', '/c.jpg'] }), '/b.jpg')
  assert.equal(firstImage({ imageUrl: '/d.jpg,/e.jpg' }), '/d.jpg')
  assert.equal(firstImage(null), PRODUCT_PLACEHOLDER)
  assert.equal(firstImage({}), '')
  assert.equal(imgOf({}), PRODUCT_PLACEHOLDER)
  assert.equal(imgOf({ image: '/x.jpg' }), '/x.jpg')
})

test('formatPrice formats numbers and guards bad input', () => {
  assert.equal(formatPrice(12), '12.00')
  assert.equal(formatPrice('3.5'), '3.50')
  assert.equal(formatPrice(null), '0.00')
  assert.equal(formatPrice(''), '0.00')
  assert.equal(formatPrice('abc'), 'abc')
})

test('formatTime formats valid dates and passes through invalid', () => {
  const s = formatTime('2026-10-02T10:00:00')
  assert.match(s, /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$/)
  // Safari 兼容：空格分隔的时间也能解析
  assert.match(formatTime('2026-10-02 10:00:00'), /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$/)
  assert.equal(formatTime(''), '')
  assert.equal(formatTime('not-a-date'), 'not-a-date')
})

test('toCents/formatCents compute in integer cents', () => {
  assert.equal(toCents('0.1'), 10)
  assert.equal(toCents(0.29), 29)
  assert.equal(toCents('abc'), 0)
  assert.equal(toCents(0.1) + toCents(0.2), 30)
  assert.equal(formatCents(30), '0.30')
  assert.equal(formatCents(12345), '123.45')
})

test('CATEGORIES includes 全部 first for nav parity', () => {
  assert.equal(CATEGORIES[0], '全部')
  assert.ok(CATEGORIES.includes('手机'))
})

test('ORDER_STATUS and ORDER_FLOW stay aligned', () => {
  for (const key of ORDER_FLOW) {
    assert.ok(ORDER_STATUS[key], `missing status meta for ${key}`)
  }
  assert.equal(ORDER_STATUS.unshipped.label, '待发货')
  assert.equal(ORDER_STATUS.cancelled.label, '已取消')
})
