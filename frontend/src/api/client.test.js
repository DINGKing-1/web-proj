import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  firstImage,
  formatPrice,
  formatTime,
  CATEGORIES,
  ORDER_STATUS,
  ORDER_FLOW
} from './client.js'

test('firstImage prefers firstImage then imageUrls then imageUrl', () => {
  assert.equal(firstImage({ firstImage: '/a.jpg' }), '/a.jpg')
  assert.equal(firstImage({ imageUrls: ['/b.jpg', '/c.jpg'] }), '/b.jpg')
  assert.equal(firstImage({ imageUrl: '/d.jpg,/e.jpg' }), '/d.jpg')
  assert.equal(firstImage(null), '/user-avatar/default.jpg')
  assert.equal(firstImage({}), '')
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
  assert.equal(formatTime(''), '')
  assert.equal(formatTime('not-a-date'), 'not-a-date')
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
