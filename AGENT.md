# 优品汇 - 二手交易平台（毕业设计）

## 项目概述
高端二手交易平台。前端 Vue 3 SPA（构建产物由 Spring Boot 静态托管），后端 Spring Boot 3.2.7 REST API，数据库 MySQL 8.0+。业务 API 保持不变。

## 技术栈
- **前端**: Vue 3 + Vue Router + Vite 6（`frontend/`，构建到 `src/main/resources/static/`）
- **后端**: Java 17 + Spring Boot 3.2.7
- **安全**: Spring Security + BCrypt 密码加密 + HttpSession 认证
- **页面托管**: SpaController 将业务路由 forward 到 `/index.html`，由 Vue Router 接管
- **数据库**: MySQL 8.0+（localhost:3306，root/dky123456，库名 seconddata）
- **ORM**: Spring Data JPA（Hibernate，ddl-auto=update）
- **架构**: Controller → Service → Repository 三层架构

## 运行方式
**前置条件**: JDK 17 + MySQL 8.0+（前端构建需 Node.js）
```bash
# 1. 构建前端（产物写入 src/main/resources/static）
cd frontend
npm install
npm run build
cd ..

# 2. 启动后端
.\.maven\apache-maven-3.9.6\bin\mvn.cmd clean spring-boot:run

# 开发调试前端（可选，代理到 8080）
cd frontend && npm run dev   # http://localhost:5173

# 停止（优雅关闭，释放8080端口）
.\stop.ps1
# 或 API关闭（Windows下 ShutdownHook 无法触发）
curl -X POST http://localhost:8080/internal/shutdown
```
访问 http://localhost:8080

## 默认账号
- **管理员**: admin / admin123
- 新注册用户默认余额: ¥10,000

---

## 数据库实体（9个）

| 实体 | 说明 |
|------|------|
| `User` | 用户（username, password, nickname, avatar, balance, role） |
| `Product` | 商品（name, category, originalPrice, secondHandPrice, status, imageUrl, sellerUsername, sellerAvatar） |
| `Order` | 订单（orderNumber, status, quantity, buyerUsername, sellerUsername） |
| `CartItem` | 购物车项 |
| `Comment` | 评论 |
| `Favorite` | 收藏 |
| `ChatRoom` | 聊天室（buyerUsername, sellerUsername） |
| `ChatMessage` | 聊天消息 |
| `Address` | 收货地址 |

---

## 项目结构

```
frontend/                             # Vue 3 + Vite 源码
├── src/
│   ├── api/client.js                 # fetch 封装（401 跳转、格式化）
│   ├── api/index.js                  # 全部后端 API 签名
│   ├── router/index.js               # 路由 + 登录守卫
│   ├── stores/user.js                # 登录态 / 购物车角标
│   ├── components/                   # Header / CategoryBar / ProductCard 等
│   └── views/                        # 全部页面（含 admin）
└── vite.config.js                    # build.outDir → src/main/resources/static

src/main/java/com/secondhand/mall/
├── SecondhandMallApplication.java    # 启动类 + ShutdownHook
├── controller/
│   ├── SpaController.java            # 页面路由 → forward:/index.html
│   ├── ApiController.java            # REST API（商品CRUD、购物车、订单等）
│   ├── ChatApiController.java        # 聊天 API
│   ├── FavoriteApiController.java    # 收藏 API
│   ├── AdminApiController.java       # 管理后台 API
│   ├── UserController.java           # 用户 API（登录/注册/头像）
│   ├── ShutdownController.java       # /internal/shutdown 端点
│   └── BaseApiController.java        # 基类（ok/fail 响应工具）
├── service/                          # 业务服务（未改动）
├── entity/                           # JPA 实体（9个）
├── repository/                       # Spring Data JPA Repository
└── config/                           # 配置（SessionFilter, GlobalExceptionHandler 等）

src/main/resources/
├── static/                           # Vite 构建产物（index.html + assets/）
└── templates/                        # 旧 Thymeleaf 模板（已废弃，仅归档）
```

---

## 页面布局规范

所有页面统一使用：`header → category-bar → main-content → footer`

| 页面 | 渲染方式 | 卡片类名 | 分页 |
|------|----------|----------|------|
| 首页 | Thymeleaf 服务端 | `product-card-premium` | 服务端（12件/页） |
| 分类页 | JS 客户端 API | `product-card-premium` | 客户端（12件/页） |
| 搜索页 | Thymeleaf 服务端 | `product-card` | 无 |
| 收藏页 | JS 客户端 API | `product-card-premium` | 无 |
| 聊天页 | inline style | 居中卡片布局 | 无 |

### 聊天页特殊布局
- 居中卡片：`.chat-container { max-width:1000px; width:90%; margin:20px auto; }`
- **使用 inline style**，不引用 style.css（旧聊天类与新设计冲突）
- 左侧联系人列表 260px + 右侧聊天区 flex:1
- 消息气泡 max-width: 260px（每行约8个中文字）

---

## API 端点

### 公开 API（无需登录）
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/login` | 登录 |
| POST | `/api/register` | 注册 |
| GET | `/api/products` | 商品列表（支持分类/价格/排序/分页） |
| GET | `/api/products/{id}` | 商品详情 |

### 需登录 API
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/session` | 当前用户信息 + 订单角标 |
| POST | `/api/cart` | 加入购物车 |
| GET | `/api/cart` | 购物车列表 |
| GET | `/api/cart/count` | 购物车数量角标 |
| DELETE | `/api/cart/{id}` | 移除购物车项 |
| DELETE | `/api/cart` | 清空购物车 |
| POST | `/api/orders` | 创建订单 |
| GET | `/api/orders` | 订单列表 |
| PUT | `/api/orders/{id}/status` | 更新订单状态 |
| POST | `/api/favorites/toggle` | 收藏/取消收藏 |
| GET | `/api/favorites` | 收藏列表 |
| DELETE | `/api/favorites/{id}` | 取消收藏 |
| POST | `/api/chat/room` | 查找/创建聊天室 |
| GET | `/api/chat/rooms` | 聊天室列表 |
| POST | `/api/chat/message` | 发送消息 |
| GET | `/api/chat/messages/{roomId}` | 消息记录 |

### 内部 API
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/internal/shutdown` | 优雅关闭（无需登录） |

---

## 核心业务规则

### 1. 头像跟随（最高优先级）
所有显示用户头像处必须实时从 User 表获取最新头像。
- `ProductService.fillSellerAvatar()` — 每次获取商品时覆盖
- `FavoriteService.getFavorites()` — 收藏列表也从 User 表获取
- 不在商品表中固定存储头像

### 2. 返回动画禁止（最高优先级）
从任何页面返回首页时，禁止任何从顶端滑下来的动画效果。
- 已移除所有 fadeInUp、heroContentIn、stagger-children 入场动画
- `history.scrollRestoration = 'manual'` 禁用浏览器滚动恢复

### 3. 登录状态管理
- 使用 `sessionStorage`（浏览器关闭自动清除）
- 禁止在 `beforeunload` 中调用 logout（页面跳转会误触发）
- `validateSessionConsistency()` 每5秒比对 HttpSession 和 sessionStorage

### 4. 购物车角标
导航栏购物车图标显示实时数量角标（`GET /api/cart/count`）。
- 刷新时机：页面加载、每5秒定时、加入/移除/清空/支付后
- 调用 `App.updateSideNavBadge()` 刷新

### 5. 商品上传简化
移除卖家姓名/电话/原价字段，用户直接输入售价。
- 后端 `ApiController.createProduct()` 自动从登录 User 获取 sellerName/sellerPhone/sellerAvatar
- `Product.sellerPhone` 已改为 nullable

### 6. 分页规则
- 每页12件商品
- 首页：服务端 Thymeleaf 分页（`?page=N`）
- 分类页：客户端 API 分页
- 分类栏"全部"按钮所有页面都需添加

### 7. 端口管理
应用结束运行时必须释放8080端口。
- `SecondhandMallApplication.java` JVM ShutdownHook
- `POST /internal/shutdown` API 端点（Windows PowerShell Stop-Process -Force 不触发 ShutdownHook）
- `stop.ps1` 脚本：先 API 关闭，再 taskkill

---

## 订单系统

### 状态流转
```
下单 → 未发货(unshipped) → 运输中(shipped) → 待收货(received) → 已完成(completed)
                ↓
            已取消(cancelled) - 退还余额
```

### 订单编号
- 随机6位数字（100000-999999），唯一性校验重试
- 实体字段：`orderNumber`(unique, nullable) + `quantity`(nullable)

### 订单详情页
- 商品信息可点击跳转
- "取消订单"按钮移至订单信息卡片内部
- 状态时间线：圆点 + 连线 + 渐变高亮

---

## 设计规范（暖金色奶油色 v2）

### 配色
- **背景**: 多层 radial-gradient `#FFF8F0 → #FFF3E6 → #FFEDDA`
- **强调**: 金色 `#E8913A` / `#D47A2A`
- **文字**: 深色 `#2D2420`，次要 `var(--color-text-secondary)`
- **成功**: 绿色 `#34c759`
- **错误**: 红色 `var(--color-error)`

### Glass 效果
仅保留于 3 个组件：导航栏、模态框、Toast。其余用纯白背景 + 细微阴影。

### z-index Token
```
--z-base:1, --z-dropdown:100, --z-sticky:200, --z-nav:300,
--z-modal-backdrop:400, --z-modal:500, --z-toast:600,
--z-tooltip:700, --z-overlay:800
```

### 字体
- 标题/正文通过 Google Fonts `@import`，保留 `display=swap`

---

## ⚠️ 开发注意事项

### Thymeleaf 陷阱
- `#dates.format()` 不兼容 LocalDateTime → 用 JS 处理
- SpEL `BigDecimal*Integer` 不自动转换 → 用 JS 计算
- `/*[[${variable}]]*/ ''` 渲染后变量永远为空 → 正确写法：`'[[${variable}]]'`
- `ddl-auto=update` + `@Column(nullable=false)` 已有数据会失败 → 先设 nullable

### JPA 陷阱
- `void deleteByUsername()` 需 `@Modifying @Transactional @Query`，否则静默失败
- `@Column(unique=true)` 不影响 nullable，可同时存在

### 前端架构（Vue3）
- 设计系统：`main.css` + `element-theme.css`（暖金奶油 + Element Plus 主题）
- 依赖：Vue 3 / Vue Router / Pinia / Element Plus（按需）/ VueUse / ECharts / dayjs
- API 层：`api/client.js` + `api/index.js`，响应字段展平到顶层
- 登录态：Pinia `stores/user.js` + 5s `/api/session`（页面隐藏时暂停）
- 角标：购物车 cartCount + 聊天 unread
- 聊天实时：GET /api/chat/stream/{roomId} SSE，断开退回轮询
- 聊天建房两段式：先查后建；发布 condition ↔ itemCondition；多图 imageUrl 逗号分隔

---

## 测试

```bash
.\.maven\apache-maven-3.9.6\bin\mvn.cmd test
```

21个测试全部通过，覆盖：AdminService、BigDecimal、CartService、ChatService、CommentService、OrderService（并发/状态流转/退款）、ProductService、UserService。

---

## 相关 Skills
- **frontend-design-pro**: 前端设计增强规范
- **web-design-engineer**: 构建精美的视觉 Web 组件
- **ui-ux-pro-max**: UI/UX 设计智能
- **sync-templates**: 多模板同步修改（`.mimocode/skills/sync-templates/SKILL.md`）

