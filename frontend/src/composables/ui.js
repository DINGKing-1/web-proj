import { ElMessage, ElMessageBox } from 'element-plus'

/** 统一 Toast：优先 Element Plus */
export function showToast(message, type = 'success') {
  const map = {
    success: 'success',
    error: 'error',
    warning: 'warning',
    info: 'info'
  }
  ElMessage({
    message,
    type: map[type] || 'success',
    duration: 2600,
    showClose: true,
    grouping: true
  })
}

/** 统一确认框 */
export function confirmDialog({
  title = '提示',
  message = '',
  okText = '确定',
  cancelText = '取消',
  type = 'info'
} = {}) {
  return ElMessageBox.confirm(message, title, {
    confirmButtonText: okText,
    cancelButtonText: cancelText,
    type: type === 'warning' ? 'warning' : type === 'error' ? 'error' : 'info',
    autofocus: false,
    customStyle: { borderRadius: '16px' }
  })
    .then(() => true)
    .catch(() => false)
}

/** 表单校验失败的统一提示 */
export function showFormError(message = '请完善必填信息') {
  showToast(message, 'warning')
}

export default { showToast, confirmDialog, showFormError }
