# 优品汇 - 二手交易平台（毕业设计）

## 项目概述
高端二手交易平台。前端 Vue 3 SPA（构建产物由 Spring Boot 静态托管），后端 Spring Boot 3.2.7 REST API，数据库 MySQL 8.0+。

**入口交互**：打开站点首屏是首页 `/`，不是登录页。会话失效时，仅访问需登录页面才跳 `/login`；公开页（首页/商品/搜索等）只清登录态并留在原页。登录页提供「先去首页逛逛」。

## 技术栈
- **前端**: Vue 3 + Vue Router + Pinia + Vite 6 + Element Plus（按需）+ VueUse + ECharts + dayjs（`frontend/`）
- **后端**: Java 17 + Spring Boot 3.2.7
- **安全**: Spring Security + BCrypt 密码加密 + HttpSession 认证
- **页面托管**: SpaController 将业务路由 forward 到 `/index.html`，由 Vue Router 接管
- **数据库**: MySQL 8.0+（localhost:3306，库名 seconddata）
- **ORM**: Spring Data JPA（Hibernate；`ddl-auto=validate`，表结构由 Flyway `db/migration` 管理）
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
# 或 API 关闭（Windows 下 ShutdownHook 无法触发；写操作需 X-Requested-With）
curl -X POST -H "X-Requested-With: XMLHttpRequest" http://localhost:8080/internal/shutdown
```

访问 http://localhost:8080

> 改 Java 后必须 `mvn clean package`，否则 `target/classes` 残留旧 class 会打进 jar。

## 默认账号
- **管理员**: admin / admin123（由 DataInitializer 以 BCrypt 创建）
- **演示买家**: demo_buyer1 / demo_buyer2 / demo_buyer3（密码 demo123456，余额 2 万）
- **演示卖家**: demo_seller1 / demo_seller2 / demo_seller3（密码 demo123456，各挂 4 件商品）

## 演示数据（DemoDataInitializer）

首次启动自动播种，**全程幂等**（每步先查是否已播种，重复启动不叠加）：
12 件演示商品覆盖全部 9 个分类、7 笔订单覆盖全部订单状态、评价/举报/收藏/未读通知各若干。

- 开关：`mall.demo-data.enabled`（默认 true，设为 false 关闭）
- 演示商品的图片**取自同分类已有商品**，保证图文相符（`imageForCategory`）
- 订单资金与余额、钱包流水保持一致；已完成单同时给卖家打款
- 「待付款」演示单 30 分钟后会被自动关单——这是真实业务行为，
  重新演示待付款状态时用测试账号下一单即可

---

## 数据库实体（17个）

| 实体 | 说明 |
|------|------|
| `User` | 用户（username, password, nickname, avatar, balance, role）；软删除 `deleted`/`deletedAt`/`deletedBy`（self\|admin） |
| `Product` | 商品（name, category, originalPrice, secondHandPrice, status, imageUrl, sellerUsername, reviewNote）；sellerPhone 序列化只写不读；status: pending(待审)/active/sold/inactive/rejected(驳回)/deleted |
| `Order` | 订单（orderNumber, status, quantity, buyerUsername, sellerUsername） |
| `CartItem` | 购物车项 |
| `Comment` | 评论（后端保留；商品详情页已不展示买家留言） |
| `Favorite` | 收藏 |
| `ChatRoom` | 聊天室（buyerUsername, sellerUsername） |
| `ChatMessage` | 聊天消息（messageType: text/image/video） |
| `Address` | 收货地址 |
| `Notification` | 站内通知（type, title, refType/refId, readFlag） |
| `Review` | 订单评价（orderId 唯一约束，rating 1-5；含卖家 reply、买家 followUpContent） |
| `Report` | 商品举报（reason, detail, status + 处置 action/handledBy/handledAt） |
| `QuickReply` | 客服快捷话术 |
| `WalletTransaction` | 钱包流水（充值/支付/退款，落库可对账） |
| `Category` | 商品分类（name 唯一、sort、enabled；后台 CRUD，前台导航读启用项） |
| `Coupon` | 优惠券（code 唯一、discountAmount 立减、minAmount 门槛、限量/限领、有效期） |
| `UserCoupon` | 用户领券（unused/used/expired + usedOrderId） |
| `AuditLog` | 管理端操作审计（operator/action/target/detail/success） |
| `Offer` | 议价出价（pending/accepted/rejected/cancelled，接受后挂 orderId） |
| `Announcement` | 首页公告/图片推荐（后台 CRUD） |
| `WithdrawRequest` | 卖家提现申请（冻结 → 管理员审批打款/驳回） |

---

## 项目结构

```
frontend/                             # Vue 3 + Vite 源码
├── src/
│   ├── api/client.js                 # fetch 封装（401 分流跳转、格式化、CATEGORIES）
│   ├── api/index.js                  # 全部后端 API 签名（含 adminApi 回收站）
│   ├── router/index.js               # 路由 + 登录/admin 守卫；`/` → HomePage
│   ├── stores/user.js                # 登录态 / 购物车 / 未读角标
│   ├── components/                   # Header / CategoryBar / ProductCard / AdminShell 等
│   └── views/                        # 全部页面（含 admin/、AdminRecyclePage）
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
├── entity/                           # JPA 实体（14个）；User.password/sessionId 已 @JsonIgnore
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
| POST | `/api/login` | 登录（已注销/软删除账号不可登录） |
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
| POST | `/api/upload-media` | 聊天/通用媒体上传（图片 ≤5MB / 视频 ≤30MB） |
| POST/PUT/DELETE | `/api/products`、`/api/products/{id}` | 发布 / 编辑 / 下架商品 |
| GET | `/api/user/products` | 我的商品 |
| POST/GET/DELETE | `/api/cart*` | 购物车 |
| POST | `/api/orders` | 创建订单 |
| POST | `/api/orders/batch` | 批量下单（同事务，全成或全败，并清理购物车） |
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
| GET/DELETE | `/api/admin/products`、`/api/admin/products/{id}` | 商品管理（列表不含 deleted；删除=移入回收站） |
| POST | `/api/admin/products/{id}/offline`、`/online` | 下架（inactive）/ 重新上架 |
| GET/DELETE | `/api/admin/users`、`/api/admin/users/{id}` | 用户管理（响应不含密码；删除=软删除进回收站） |
| POST | `/api/admin/users/{id}/ban` | 封禁 / 解封 |
| GET | `/api/admin/recycle/products`、`/api/admin/recycle/users` | 回收站列表 |
| POST | `/api/admin/recycle/products/{id}/restore`、`/api/admin/recycle/users/{id}/restore` | 恢复商品 / 用户（用户恢复含其商品） |
| DELETE | `/api/admin/recycle/products/{id}`、`/api/admin/recycle/users/{id}` | 彻底删除（不可恢复；用户会 purge 业务数据） |

### 内部 / 运维 API
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/internal/shutdown` 或 `/api/internal/shutdown` | 本地运维关闭（仅本机 + admin，stop.ps1 使用） |
| POST | `/api/graceful-shutdown` | 管理员优雅关闭（SpringApplication.exit） |

### 本阶段新增 API
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/categories` | 启用中的分类（公开，前台导航/筛选用） |
| GET/POST/PUT/DELETE | `/api/admin/categories*` | 分类 CRUD（含各分类商品数） |
| POST | `/api/admin/products/{id}/approve`、`/reject` | 商品审核（驳回带理由，均通知卖家） |
| POST | `/api/orders/{id}/after-sale/cancel` | 买家撤销售后申请（未寄回时） |
| POST | `/api/orders/{id}/return-tracking` | 买家填写退货运单号 |
| POST | `/api/orders/{id}/return-received` | 卖家确认收到退货 → 退款 |
| GET | `/api/admin/audit-logs` | 操作审计分页查询（支持 operator 筛选） |
| GET | `/api/admin/export/orders`、`/users`、`/wallet` | CSV 导出（UTF-8 BOM，Excel 可直接打开） |
| GET | `/swagger-ui.html`、`/v3/api-docs` | 接口文档（springdoc，115 个路径） |
| GET | `/api/coupons/claimable`、`/api/coupons/mine` | 可领取 / 我的优惠券 |
| POST | `/api/coupons/{id}/claim` | 领取优惠券 |
| GET/POST/PUT/DELETE | `/api/admin/coupons*` | 优惠券发放与管理（已发放的删除会自动转停用） |
| POST | `/api/orders/{id}/pay`（body 可带 userCouponId） | 支付时核销优惠券 |
| GET | `/api/seller/stats` | 卖家经营看板（商品/成交/待结算/已到账/近 14 天趋势） |
| POST | `/api/reviews/{id}/reply`、`/api/reviews/{id}/followup` | 卖家回复评价 / 买家追评（一条评价一次） |
| PUT | `/api/admin/reports/{id}`（body 可带 action） | 举报处置：`none` 仅标记 / `offline` 下架 / `delete` 进回收站 |

### 审计日志（AOP）
- 注解 `@Audited(action=..., targetType=...)` 标在**服务层**敏感方法上
  （`aspect/AuditAspect.java`），无论成功失败都写 `audit_logs`
- 已覆盖：商品审核/上下架/删除/恢复/彻底删除、用户封禁/删除/恢复/彻底删除、
  强制取消订单、举报处理、提现审批、分类与公告增删改
- 操作人取当前请求 session 的 `username`；detail 用 Jackson 序列化入参

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
- 15s 轮询 `/api/session` 一致性校验（页面隐藏时暂停）
- **401 分流**：公开页只清状态不跳登录；仅 AUTH_PATH 前缀或被踢（kicked）才跳 `/login`。被踢且在公开页时回 `/?kicked=1`
- 禁止在 `beforeunload` 中调用 logout

### 4. 角标
- 购物车：`GET /api/cart/count`
- 聊天未读：`GET /api/chat/unread`
- 订单：`/api/session` 返回 buyerOrderCount / sellerOrderCount

### 5. 商品
- 上传：用户只填售价；后端从登录 User 填 seller 信息
- 我的商品：支持编辑、上下架（status active/inactive）、软删除（status=deleted）
- **发布审核**（`mall.product.review-required`，默认 true）：新商品先进 `pending`，
  管理员「通过」→ active /「驳回」→ rejected（理由写 `reviewNote`）并站内通知卖家；
  卖家改内容自动重新排队为 pending，且不能自行把待审/驳回改成 active。
  仅 active 可下单/加购/议价。关闭审核：环境变量 `PRODUCT_REVIEW_REQUIRED=false`
- **管理端删除/下架语义**：下架 → `inactive`（可再上架）；删除 → `deleted` 进回收站（主列表不显示，可恢复或彻底删除）
- 多图：`imageUrl` 逗号分隔，序列化另有 `imageUrls`/`firstImage`
- 发布请求 `condition` ↔ 实体 `itemCondition`
- **商品详情页无「买家留言」区**；沟通走右侧/店铺条「联系卖家」（聊天）

### 6. 回收站（可追回）
- **商品**：`status=deleted` 软删除；恢复 → `active`；彻底删除才物理删
- **用户**：自助注销 / 管理员删除均为软删除（`deleted=true` + `deletedAt`/`deletedBy`），业务数据保留；登录拒绝已删用户；恢复用户时一并恢复其名下 deleted 商品；「彻底删除」才 `purgeUserData` + 物理删
- 后台页面：`/admin/recycle`（`AdminRecyclePage`，商品/用户两个 Tab）

### 6. 分页规则
- 首页最新上架：每页 25 件（5 列网格 + 无限滚动）
- `GET /api/products` 与 `/api/products/search` 均为手动分页
- `sortBy` ∈ newest / sales（销量降序）/ price-asc / price-desc
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

售后（shipped / received 可申请）：
after_sale(待卖家处理) → return_shipping(待买家寄回) → return_sent(已寄回) → refunded(已退款)
        ↓ 卖家拒绝 / 买家撤销                ↓ 卖家超时 72h → 系统自动同意
   回到申请前状态
```

### 要点
- 订单编号：时间戳 + 4 位随机（撞号重试）
- 取消仅限 unshipped；发货写 trackingNumber
- 结算走 `POST /api/orders/batch`：同事务全成或全败，并清理购物车项
- **议价单**走 `OrderService.createNegotiatedOrder`：按出价成交，订单金额记协商价；
  与挂牌价的一致性校验只作用于普通下单（防客户端篡改）
- **退货售后是两段式**：卖家「同意退货」只改状态不退款；买家填 `returnTrackingNumber`
  寄回后，卖家「确认收货」才 `settleRefund`（退款+回补库存）→ `refunded`。
  这样避免钱货两空。`refunded` 不计入销量/GMV/待结算。
- **超时兜底**：`OrderAfterSaleTimeoutService` 每 10 分钟扫一次，
  `after_sale` 超 72 小时未处理 → 自动同意退货并双向通知
- **平台仲裁**：管理员「强制取消」对 after_sale/return_* 同样有效（直接退款结案）

---

## 设计系统（Vue3）

- Tokens 定义于 `frontend/src/styles/main.css` 与 `element-theme.css`
- 暖金奶油主视觉 + Element Plus 按需组件（unplugin-vue-components）
- 布局：`AppHeader + CategoryBar + main + AppFooter`；admin 用 `AdminShell` 独立布局
- **品牌 Logo**：「优」字圆角标（渐变 accent）；前台 Header / 登录页 / 管理端侧栏与顶栏管理员头像共用该 Logo 标
- **动效**：Hero 有光斑漂浮、轨道旋转、星光、标题错落入场与渐变流光；全站有页面/卡片/按钮微交互。**入场均为自下而上（rise-in），禁止从顶端滑落**；遵守 `prefers-reduced-motion`
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
- 单会话：异地登录踢下线（401）；公开页不跳登录，需登录页/被踢才进 `/login?kicked=1`
- Element Plus 勿全量 `app.use`，主包会超过 1MB

### 安全
- `User.password` / `sessionId` 已 `@JsonIgnore`，API 不返回
- **口令不入库**：数据库/邮箱口令放 `config/secrets.properties`（已 gitignore），由
  `spring.config.import=optional:file:./config/secrets.properties` 引入；
  环境变量 `MYSQL_PASSWORD` / `MAIL_PASSWORD` 优先级更高，可覆盖
- **写操作 CSRF**：`ApiCsrfHeaderFilter` 要求 `X-Requested-With: XMLHttpRequest`；
  前端 `client.js` 自动注入，`stop.ps1`/`restart.ps1` 关服请求同样带该头
- **模拟充值**：`mall.wallet.mock-recharge-enabled`（生产 false）+ 单笔/累计上限；
  新用户初始余额 `mall.user.initial-balance`（生产 0）
- **管理员口令**：仅首次创建时写入（`MALL_ADMIN_PASSWORD` 或配置项；空则随机生成并打印一次）；
  **绝不覆盖已有密码**，也不把异常口令批量重置成弱口令
- **订单快照**：名称/分类/图片一律取服务端商品，防伪造；运单号仅发货时写入
- **CSV 导出**：公式字符（=`+`-`@）前置单引号，防 Excel 公式注入
- **找回密码验证码**默认不落日志（`mall.security.log-mail-codes`）
- **关服接口**：拒绝带 `X-Forwarded-For`/`X-Real-IP` 的请求，防反代绕过「仅本机」
- 敏感词校验统一在 `ProductService.createProduct/updateProduct`（合并后校验，
  防止只改一个字段绕过），评论/评价/聊天仍由 Controller 校验

---

## 测试

```bash
.\.maven\apache-maven-3.9.6\bin\mvn.cmd test   # 后端 45 个
cd frontend && npm test                        # 前端工具函数冒烟
cd frontend && npm run lint                    # ESLint（JS 源码）
```

后端覆盖 AdminService、BigDecimal、CartService、ChatService、CommentService、
OrderService（批量下单事务、议价成交价、退货全链路、超时售后、优惠券核销与退券）、
ProductService（敏感词复检、发布审核）、UserService。
