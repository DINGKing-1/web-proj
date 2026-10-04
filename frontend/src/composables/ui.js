/**
 * 统一反馈层：自绘 Toast / 确认框
 * 与 main.css 的 .toast / .modal-overlay 设计系统对齐，
 * 不再使用 ElMessage（grouping 会在右上角叠出计数角标，且默认样式与主题割裂）。
 */

const TOAST_ICONS = {
  success: 'fa-circle-check',
  error: 'fa-circle-xmark',
  warning: 'fa-triangle-exclamation',
  info: 'fa-circle-info'
}

let toastHost = null

function ensureToastHost() {
  if (toastHost && document.body.contains(toastHost)) return toastHost
  toastHost = document.createElement('div')
  toastHost.className = 'toast-host'
  toastHost.setAttribute('role', 'status')
  toastHost.setAttribute('aria-live', 'polite')
  document.body.appendChild(toastHost)
  return toastHost
}

/** 统一 Toast（不合并、不显示计数角标） */
export function showToast(message, type = 'success') {
  const host = ensureToastHost()
  const el = document.createElement('div')
  el.className = `toast ${type}`
  el.innerHTML = `<i class="fas ${TOAST_ICONS[type] || TOAST_ICONS.info}"></i><span></span>`
  el.querySelector('span').textContent = String(message ?? '')
  host.appendChild(el)

  const timer = setTimeout(() => dismiss(), 2800)
  function dismiss() {
    clearTimeout(timer)
    el.classList.add('leaving')
    setTimeout(() => el.remove(), 240)
  }
  el.addEventListener('click', dismiss)
}

/** 统一确认框：返回 Promise<boolean> */
export function confirmDialog({
  title = '提示',
  message = '',
  okText = '确定',
  cancelText = '取消',
  type = 'info'
} = {}) {
  return new Promise((resolve) => {
    const overlay = document.createElement('div')
    overlay.className = 'modal-overlay'
    overlay.setAttribute('role', 'dialog')
    overlay.setAttribute('aria-modal', 'true')

    const iconMap = {
      info: { cls: 'fa-circle-info', color: 'var(--info)' },
      warning: { cls: 'fa-triangle-exclamation', color: 'var(--warning)' },
      error: { cls: 'fa-circle-xmark', color: 'var(--danger)' },
      success: { cls: 'fa-circle-check', color: 'var(--success)' }
    }
    const icon = iconMap[type] || iconMap.info

    overlay.innerHTML = `
      <div class="modal-box confirm-box">
        <div class="confirm-icon" style="color:${icon.color}">
          <i class="fas ${icon.cls}"></i>
        </div>
        <h2 class="confirm-title"></h2>
        <p class="confirm-msg"></p>
        <div class="confirm-actions">
          <button type="button" class="btn btn-outline confirm-cancel"></button>
          <button type="button" class="btn btn-primary confirm-ok"></button>
        </div>
      </div>
    `
    overlay.querySelector('.confirm-title').textContent = title
    overlay.querySelector('.confirm-msg').textContent = message
    overlay.querySelector('.confirm-cancel').textContent = cancelText
    overlay.querySelector('.confirm-ok').textContent = okText

    const okBtn = overlay.querySelector('.confirm-ok')
    const cancelBtn = overlay.querySelector('.confirm-cancel')

    function close(val) {
      overlay.classList.add('leaving')
      setTimeout(() => {
        overlay.remove()
        document.removeEventListener('keydown', onKey)
      }, 180)
      resolve(val)
    }

    function onKey(e) {
      if (e.key === 'Escape') close(false)
      if (e.key === 'Enter') close(true)
    }

    okBtn.addEventListener('click', () => close(true))
    cancelBtn.addEventListener('click', () => close(false))
    overlay.addEventListener('click', (e) => {
      if (e.target === overlay) close(false)
    })
    document.addEventListener('keydown', onKey)

    document.body.appendChild(overlay)
    okBtn.focus()
  })
}

/** 表单校验失败的统一提示 */
export function showFormError(message = '请完善必填信息') {
  showToast(message, 'warning')
}

/** 统一输入框：返回 Promise<string|null>（取消为 null） */
export function promptDialog({
  title = '提示',
  message = '',
  placeholder = '',
  defaultValue = '',
  okText = '确定',
  cancelText = '取消',
  required = false
} = {}) {
  return new Promise((resolve) => {
    const overlay = document.createElement('div')
    overlay.className = 'modal-overlay'
    overlay.setAttribute('role', 'dialog')
    overlay.setAttribute('aria-modal', 'true')

    overlay.innerHTML = `
      <div class="modal-box confirm-box">
        <h2 class="confirm-title"></h2>
        <p class="confirm-msg"></p>
        <textarea class="form-input prompt-input" rows="3"></textarea>
        <div class="confirm-actions">
          <button type="button" class="btn btn-outline confirm-cancel"></button>
          <button type="button" class="btn btn-primary confirm-ok"></button>
        </div>
      </div>
    `
    overlay.querySelector('.confirm-title').textContent = title
    overlay.querySelector('.confirm-msg').textContent = message
    overlay.querySelector('.confirm-cancel').textContent = cancelText
    overlay.querySelector('.confirm-ok').textContent = okText
    const input = overlay.querySelector('.prompt-input')
    input.placeholder = placeholder
    input.value = defaultValue

    const okBtn = overlay.querySelector('.confirm-ok')
    const cancelBtn = overlay.querySelector('.confirm-cancel')

    function close(val) {
      overlay.classList.add('leaving')
      setTimeout(() => {
        overlay.remove()
        document.removeEventListener('keydown', onKey)
      }, 180)
      resolve(val)
    }

    function onKey(e) {
      if (e.key === 'Escape') close(null)
      if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
        const v = input.value.trim()
        if (required && !v) return
        close(v || null)
      }
    }

    okBtn.addEventListener('click', () => {
      const v = input.value.trim()
      if (required && !v) {
        showToast('请填写内容', 'warning')
        input.focus()
        return
      }
      close(v || null)
    })
    cancelBtn.addEventListener('click', () => close(null))
    overlay.addEventListener('click', (e) => {
      if (e.target === overlay) close(null)
    })
    document.addEventListener('keydown', onKey)

    document.body.appendChild(overlay)
    input.focus()
  })
}

export default { showToast, confirmDialog, showFormError, promptDialog }
