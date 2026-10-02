# 优品汇 - 二手交易平台（毕业设计）

## 项目概述
高端二手交易平台。前端 Vue 3 SPA（构建产物由 Spring Boot 静态托管），后端 Spring Boot 3.2.7 REST API，数据库 MySQL 8.0+。业务 API 保持不变。

## 技术栈
- **前端**: Vue 3 + Vue Router + Pinia + Vite 6 + Element Plus（按需）+ VueUse + ECharts + dayjs（`frontend/`）
- **后端**: Java 17 + Spring Boot 3.2.7
- **安全**: Spring Security + BCrypt 密码加密 + HttpSession 认证
- **页面托管**: SpaController 将业务路由 forward 到 `/index.html`，由 Vue Router 接管
- **数据库**: MySQL 8.0+（localhost:3306，库名 seconddata）
- **ORM**: Spring Data JPA（Hibernate，ddl-auto=update）
- **架构**: Controller → Service → Repository 三层架构

## 运行方式
**前置条件**: JDK 17 + MySQL 8.0+ + Node.js

```bash
# 1. 构建前端（产物写入 src/main/resources/static）
cd frontend
npm install
npm run build
cd ..

# 2. 启动后端（数据库密码可用环境变量 MYSQL_PASSWORD 覆盖）
.\.maven\apache-maven-3.9.6\bin\mvn.cmd clean spring-boot:run

# 开发调试前端（可选，代理到 8080）
cd frontend && npm run dev   # http://localhost:5173

# 停止（优雅关闭，释放 8080 端口）
.\stop.ps1
# 或 API 关闭（Windows 下 ShutdownHook 无法触发）
curl -X POST http://localhost:8080/internal/shutdown
```

访问 http://localhost:8080

> 改 Java 后必须 `mvn clean package`，否则 `target/classes` 残留旧 class 会打进 jar。

## 默认账号
- **管理员**: admin / admin123（由 DataInitializer 以 BCrypt 创建）

---

## 数据库实体（9个）

| 实体 | 说明 |
|------|------|
| `User` | 用户（username, password, nickname, avatar, balance, role） |
| `Product` | 商品（name, category, originalPrice, secondHandPrice, status, imageUrl, sellerUsername） |
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
│   ├── api/client.js                 # fetch 封装（401 跳转、格式化、CATEGORIES）
│   ├── api/index.js                  # 全部后端 API 签名
│   ├── router/index.js               # 路由 + 登录/admin 守卫
│   ├── stores/user.js                # 登录态 / 购物车 / 未读角标
│   ├── components/                   # Header / CategoryBar / ProductCard / AdminShell 等
│   └── views/                        # 全部页面（含 admin/）
└── vite.config.js                    # build.outDir → src/main/resources/static

src/main/java/com/secondhand/mall/
├── SecondhandMallApplication.java    # 启动类 + ShutdownHook
├── controller/
│   ├── SpaController.java            # 页面路由 → forward:/index.html
│   ├── ApiController.java            # REST API（商品/购物车/订单/地址/用户）
│   ├── ChatApiController.java        # 聊天 API
│   ├── ChatSseController.java        # 聊天 SSE
│   ├── FavoriteApiController.java    # 收藏 API
│   ├── AdminApiController.java       # 管理后台 API
│   ├── UserController.java           # 用户 API（登录/注册/头像）
│   ├── ShutdownController.java       # /internal/shutdown 端点
│   └── BaseApiController.java        # 基类（ok/fail 响应工具）
├── service/                          # 业务服务
├── entity/                           # JPA 实体（9个）；User.password/sessionId 已 @JsonIgnore
├── repository/                       # Spring Data JPA Repository
└── config/                           # 配置（SessionFilter, SecurityConfig 等）

src/main/resources/
└── static/                           # Vite 构建产物（index.html + assets/）
```

---

## API 端点

### 公开 API（无需登录）
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/login` | 登录 |
| POST | `/api/register` | 注册 |
| POST | `/api/admin/login` | 管理员登录 |
| GET | `/api/session` | 当前会话信息 |
| GET | `/api/products` | 商品列表（支持分类/价格/排序/分页） |
| GET | `/api/products/{id}` | 商品详情 |
| GET | `/api/products/search` | 商品搜索（关键字 + 筛选） |
| GET | `/api/products/{id}/comments` | 商品评论 |

### 需登录 API
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/logout` | 退出 |
| POST | `/api/upload-image` | 图片上传（Base64） |
| POST/PUT/DELETE | `/api/products`、`/api/products/{id}` | 发布 / 编辑 / 下架商品 |
| GET | `/api/user/products` | 我的商品 |
| POST/GET/DELETE | `/api/cart*` | 购物车 |
| POST | `/api/orders` | 创建订单 |
| GET | `/api/orders`、`/api/orders/{id}` | 订单列表 / 详情 |
| PUT | `/api/orders/{id}` | 更新订单状态（status, trackingNumber） |
| POST | `/api/orders/{id}/cancel`、`/complete` | 取消 / 完成 |
| GET | `/api/seller/orders` | 卖家订单 |
| POST/GET/DELETE | `/api/favorites*` | 收藏 |
| POST/GET/DELETE | `/api/chat*`、`/api/chat/stream/{roomId}` | 聊天 + SSE |
| GET/POST/PUT/DELETE | `/api/addresses*` | 收货地址 |
| GET/PUT | `/api/user`、`/api/user/avatar` | 用户资料 / 头像 |
| GET | `/api/cart/count`、`/api/chat/unread` | 角标 |

### 管理端 API（需 admin）
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/stats` | 统计 |
| GET/DELETE | `/api/admin/products`、`/api/admin/products/{id}` | 商品管理 |
| GET/DELETE | `/api/admin/users`、`/api/admin/users/{id}` | 用户管理（响应不含密码） |

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

### 3. 登录状态管理
- Pinia `stores/user.js` + `sessionStorage.loginUser`
- 5s 轮询 `/api/session` 一致性校验（页面隐藏时暂停）
- 禁止在 `beforeunload` 中调用 logout

### 4. 角标
- 购物车：`GET /api/cart/count`
- 聊天未读：`GET /api/chat/unread`
- 订单：`/api/session` 返回 buyerOrderCount / sellerOrderCount

### 5. 商品
- 上传：用户只填售价；后端从登录 User 填 seller 信息
- 我的商品：支持编辑、上下架（status active/inactive）、软删除（status=deleted）
- 多图：`imageUrl` 逗号分隔，序列化另有 `imageUrls`/`firstImage`
- 发布请求 `condition` ↔ 实体 `itemCondition`

### 6. 分页规则
- 每页 12 件商品
- `GET /api/products` 与 `/api/products/search` 均为手动分页
- `sortBy` ∈ newest / price-asc / price-desc
- 分类「全部」需省略 category 参数（后端字面匹配）

### 7. 端口管理
应用结束运行时必须释放 8080 端口。
- JVM ShutdownHook + `POST /internal/shutdown`
- `stop.ps1`：先 API 关闭，再 taskkill

---

## 订单系统

### 状态流转
```
下单 → 未发货(unshipped) → 运输中(shipped) → 待收货(received) → 已完成(completed)
                ↓
            已取消(cancelled) - 退还余额
```

### 要点
- 订单编号：随机 6 位数字，唯一性校验重试
- 取消仅限 unshipped；发货写 trackingNumber
- 下单非原子：逐件 `POST /api/orders` + 删购物车项

---

## 设计系统（Vue3）

- Tokens 定义于 `frontend/src/styles/main.css` 与 `element-theme.css`
- 暖金奶油主视觉 + Element Plus 按需组件（unplugin-vue-components）
- 布局：`AppHeader + CategoryBar + main + AppFooter`；admin 用 `AdminShell` 独立布局
- 移动端：聊天双栏切换、筛选抽屉、Header 移动搜索

---

## ⚠️ 开发注意事项

### JPA 陷阱
- `void deleteByUsername()` 需 `@Modifying @Transactional @Query`，否则静默失败
- `ddl-auto=update` + `@Column(nullable=false)` 已有数据会失败 → 先设 nullable
- 改 Java 必须 `mvn clean package`

### 前端
- 响应信封扁平：读 `success`，无 `data` 包装；多数业务失败 HTTP 200 + success:false
- 认证：HttpSession + JSESSIONID，必须 `credentials: 'include'`
- 单会话：异地登录踢下线（401，跳 `/login?kicked=1`）
- Element Plus 勿全量 `app.use`，主包会超过 1MB

### 安全
- `User.password` / `sessionId` 已 `@JsonIgnore`，API 不返回
- 数据库密码用环境变量 `MYSQL_PASSWORD` 覆盖；勿把真实凭据写进文档

---

## 测试

```bash
.\.maven\apache-maven-3.9.6\bin\mvn.cmd test
```

21 个测试全部通过，覆盖 AdminService、BigDecimal、CartService、ChatService、CommentService、OrderService、ProductService、UserService。
