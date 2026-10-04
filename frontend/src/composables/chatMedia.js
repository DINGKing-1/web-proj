/** 与后端 FileUploadService 对齐：图片 5MB、视频 30MB */
export const MAX_IMAGE_SIZE = 5 * 1024 * 1024
export const MAX_VIDEO_SIZE = 30 * 1024 * 1024

export function isVideoFile(file) {
  return !!file && String(file.type || '').startsWith('video/')
}

export function isImageFile(file) {
  return !!file && String(file.type || '').startsWith('image/')
}

/** 校验聊天媒体文件；合法返回 null，否则返回提示文案 */
export function validateChatMedia(file) {
  if (!file) return '请选择文件'
  const video = isVideoFile(file)
  const image = isImageFile(file)
  if (!image && !video) return '仅支持图片或视频'
  if (image && file.size > MAX_IMAGE_SIZE) return '图片不能超过 5MB'
  if (video && file.size > MAX_VIDEO_SIZE) return '视频不能超过 30MB'
  return null
}

export function readFileAsDataUrl(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result)
    reader.onerror = () => reject(new Error('read-failed'))
    reader.readAsDataURL(file)
  })
}
