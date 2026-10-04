import test from 'node:test'
import assert from 'node:assert/strict'
import {
  MAX_IMAGE_SIZE,
  MAX_VIDEO_SIZE,
  isVideoFile,
  isImageFile,
  validateChatMedia
} from './chatMedia.js'

test('isImageFile / isVideoFile 按 MIME 判定', () => {
  assert.equal(isImageFile({ type: 'image/png' }), true)
  assert.equal(isImageFile({ type: 'video/mp4' }), false)
  assert.equal(isVideoFile({ type: 'video/mp4' }), true)
  assert.equal(isVideoFile({ type: 'image/jpeg' }), false)
  assert.equal(isImageFile(null), false)
})

test('validateChatMedia 拒绝非媒体文件', () => {
  assert.match(validateChatMedia({ type: 'application/pdf', size: 100 }), /仅支持/)
  assert.match(validateChatMedia(null), /请选择/)
})

test('validateChatMedia 图片超 5MB 拒绝', () => {
  const over = { type: 'image/jpeg', size: MAX_IMAGE_SIZE + 1 }
  assert.match(validateChatMedia(over), /5MB/)
  const ok = { type: 'image/jpeg', size: MAX_IMAGE_SIZE }
  assert.equal(validateChatMedia(ok), null)
})

test('validateChatMedia 视频 30MB 边界', () => {
  const over = { type: 'video/mp4', size: MAX_VIDEO_SIZE + 1 }
  assert.match(validateChatMedia(over), /30MB/)
  const ok = { type: 'video/mp4', size: MAX_VIDEO_SIZE }
  assert.equal(validateChatMedia(ok), null)
})
