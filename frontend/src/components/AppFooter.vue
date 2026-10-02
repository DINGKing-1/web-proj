<template>
  <footer class="site-footer">
    <div class="container footer-inner">
      <div>
        <div class="brand">优品汇</div>
        <p class="muted text-sm">精选好物 · 安心流转 · 让闲置更有价值</p>
      </div>
      <div class="links">
        <router-link to="/">首页</router-link>
        <router-link to="/upload">发布商品</router-link>
        <router-link to="/my-orders">我的订单</router-link>
        <router-link to="/messages">消息</router-link>
      </div>
      <div class="muted text-sm">© {{ new Date().getFullYear() }} 优品汇</div>
    </div>
  </footer>
</template>

<style scoped>
.site-footer {
  margin-top: 40px;
  border-top: 1px solid var(--border);
  background: rgba(255, 252, 248, 0.7);
  padding: 28px 0;
}
.footer-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  flex-wrap: wrap;
}
.brand {
  font-weight: 700;
  font-size: 16px;
}
.links {
  display: flex;
  gap: 18px;
  flex-wrap: wrap;
}
.links a {
  font-size: 13px;
  color: var(--ink-soft);
}
.links a:hover {
  color: var(--accent-deep);
}
</style>
