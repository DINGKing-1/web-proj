package com.secondhand.mall.config;

import com.secondhand.mall.constant.ProductStatus;
import com.secondhand.mall.entity.Favorite;
import com.secondhand.mall.entity.Notification;
import com.secondhand.mall.entity.Order;
import com.secondhand.mall.entity.Product;
import com.secondhand.mall.entity.Report;
import com.secondhand.mall.entity.Review;
import com.secondhand.mall.entity.User;
import com.secondhand.mall.repository.FavoriteRepository;
import com.secondhand.mall.repository.NotificationRepository;
import com.secondhand.mall.repository.OrderRepository;
import com.secondhand.mall.repository.ProductRepository;
import com.secondhand.mall.repository.ReportRepository;
import com.secondhand.mall.repository.ReviewRepository;
import com.secondhand.mall.repository.UserRepository;
import com.secondhand.mall.service.WalletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 演示数据：首次启动时补齐「像一个正在运营的平台」所需的账号、商品、订单、评价、举报。
 *
 * 全程幂等——每一步先判断是否已播种，重复启动不会叠加数据。
 * 关闭：mall.demo-data.enabled=false
 *
 * 演示账号统一密码 demo123456：
 *   买家 demo_buyer1/2/3  卖家 demo_seller1/2/3
 */
@Component
@ConditionalOnProperty(name = "mall.demo-data.enabled", havingValue = "true", matchIfMissing = true)
public class DemoDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    private static final String PWD = "demo123456";
    private static final String BUYER1 = "demo_buyer1";
    private static final String BUYER2 = "demo_buyer2";
    private static final String BUYER3 = "demo_buyer3";
    private static final String SELLER1 = "demo_seller1";
    private static final String SELLER2 = "demo_seller2";
    private static final String SELLER3 = "demo_seller3";

    /** 演示商品：卖家, 名称, 分类, 成色, 原价, 二手价, 库存, 描述 */
    private record DemoProduct(String seller, String name, String category, String condition,
                               double original, double price, int quantity, String description) {}

    private static final List<DemoProduct> DEMO_PRODUCTS = List.of(
        new DemoProduct(SELLER1, "iPhone 13 128G 国行双卡 无拆修", "手机", "99新",
            4200, 2680, 3, "自用一年，功能全好，电池健康 89%，配原装数据线，支持当面验机。"),
        new DemoProduct(SELLER1, "Redmi K60 12+256G 墨羽", "手机", "95新",
            2499, 1350, 2, "换机出售，无磕碰无划痕，屏幕一直贴膜使用，配件齐全。"),
        new DemoProduct(SELLER1, "联想小新 Air 14 锐龙版", "笔记本电脑", "95新",
            4599, 2300, 2, "办公本，平时只做文档和网页，硬盘通电时间短，带原装充电器。"),
        new DemoProduct(SELLER1, "JBL Flip6 蓝牙音箱 防水", "其他", "9成新",
            799, 329, 4, "买来露营用过两次，音质扎实，续航正常，包邮出。"),
        new DemoProduct(SELLER2, "索尼 A6000 微单套机 16-50mm", "相机", "95新",
            3500, 2100, 1, "入门微单，快门数不到 8000，成像锐利，含电池两块和相机包。"),
        new DemoProduct(SELLER2, "AirPods Pro 2 国行", "耳机", "99新",
            1899, 999, 2, "耳塞更换过，降噪正常，序列号可官网查询，盒子无划痕。"),
        new DemoProduct(SELLER2, "透明防摔手机壳 iPhone 15 系列", "手机壳", "全新",
            59, 19, 12, "囤多了没用完，全新未拆，气囊防摔，多色可选。"),
        new DemoProduct(SELLER2, "格力 1.5匹 变频冷暖空调 挂机", "空调", "8成新",
            3200, 1500, 1, "搬家出，制冷制热都正常，可帮找师傅拆装（费用买家承担）。"),
        new DemoProduct(SELLER3, "海尔 三门冰箱 216L 静音", "冰箱", "9成新",
            2600, 900, 1, "用了一年半，无异响不结霜，因为换大的所以出手，需自提。"),
        new DemoProduct(SELLER3, "高腰百褶半身裙 通勤款", "半身裙", "95新",
            299, 89, 3, "M 码，只穿过两次，版型挺括不皱，拍照有色差以实物为准。"),
        new DemoProduct(SELLER3, "戴尔 Latitude E6410 商务本", "笔记本电脑", "8成新",
            1800, 680, 1, "老机型但还能用，装了 Win7，适合当备用机或拿来练手。"),
        new DemoProduct(SELLER3, "宜家 LED 台灯 可调光", "其他", "9成新",
            129, 45, 5, "毕业离校出，三档色温，接口正常，宿舍神器。")
    );

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final ReviewRepository reviewRepository;
    private final ReportRepository reportRepository;
    private final FavoriteRepository favoriteRepository;
    private final NotificationRepository notificationRepository;
    private final WalletService walletService;
    private final BCryptPasswordEncoder passwordEncoder;

    public DemoDataInitializer(UserRepository userRepository, ProductRepository productRepository,
                               OrderRepository orderRepository, ReviewRepository reviewRepository,
                               ReportRepository reportRepository, FavoriteRepository favoriteRepository,
                               NotificationRepository notificationRepository, WalletService walletService,
                               BCryptPasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.reviewRepository = reviewRepository;
        this.reportRepository = reportRepository;
        this.favoriteRepository = favoriteRepository;
        this.notificationRepository = notificationRepository;
        this.walletService = walletService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        try {
            ensureUsers();
            List<Product> created = ensureProducts();
            ensureOrders(created);
            ensureReviews();
            topUpExistingReviews();
            ensureExtras();
        } catch (Exception e) {
            log.warn("演示数据初始化跳过: {}", e.getMessage());
        }
    }

    // ========== 1. 账号 ==========

    private void ensureUsers() {
        createUser(BUYER1, "小鱼干", "buyer", 20000);
        createUser(BUYER2, "阿橙", "buyer", 20000);
        createUser(BUYER3, "栗子", "buyer", 20000);
        createUser(SELLER1, "数码老张", "seller", 30000);
        createUser(SELLER2, "优品小铺", "seller", 30000);
        createUser(SELLER3, "校园二手君", "seller", 30000);
    }

    private void createUser(String username, String nickname, String kind, double balance) {
        if (userRepository.existsByUsername(username)) return;
        User u = new User();
        u.setUsername(username);
        u.setPassword(passwordEncoder.encode(PWD));
        u.setNickname(nickname);
        u.setBalance(BigDecimal.valueOf(balance));
        u.setRole("user");
        u.setEmail(username + "@demo.local");
        userRepository.save(u);
        log.info("✓ 演示{}账号已创建: {} / {}", kind, username, PWD);
    }

    // ========== 2. 商品 ==========

    /** 返回本次新建的演示商品；已播种则返回空表 */
    private List<Product> ensureProducts() {
        List<Product> existing = productRepository.findAll();
        boolean hasDemo = existing.stream()
                .anyMatch(p -> p.getSellerUsername() != null && p.getSellerUsername().startsWith("demo_seller"));
        if (hasDemo) return List.of();

        List<Product> created = new ArrayList<>(DEMO_PRODUCTS.size());
        for (DemoProduct dp : DEMO_PRODUCTS) {
            User seller = userRepository.findByUsername(dp.seller()).orElse(null);
            if (seller == null) continue;

            Product p = new Product();
            p.setName(dp.name());
            p.setCategory(dp.category());
            p.setDescription(dp.description());
            p.setOriginalPrice(BigDecimal.valueOf(dp.original()));
            p.setSecondHandPrice(BigDecimal.valueOf(dp.price()));
            p.setItemCondition(dp.condition());
            p.setSellerUsername(dp.seller());
            p.setSellerName(seller.getNickname());
            p.setSellerPhone(seller.getPhone() != null ? seller.getPhone() : "");
            p.setSellerAvatar(seller.getAvatar());
            p.setQuantity(dp.quantity());
            p.setStatus(ProductStatus.ACTIVE);
            p.setImageUrl(imageForCategory(dp.category(), existing));
            // 演示用浏览量，避免首页/排序看起来像空站
            p.setViewCount(ThreadLocalRandom.current().nextInt(80, 900));
            created.add(productRepository.save(p));
        }
        log.info("✓ 已创建 {} 个演示商品", created.size());
        return created;
    }

    /**
     * 图片取自同分类已有商品，保证图文相符；
     * 全库为空（全新环境）时退回任意商品图，都没有则留空由前端占位。
     */
    private String imageForCategory(String category, List<Product> pool) {
        String fallback = null;
        for (Product p : pool) {
            if (p.getImageUrl() == null || p.getImageUrl().isBlank()) continue;
            if (fallback == null) fallback = p.getImageUrl();
            if (category.equals(p.getCategory())) return p.getImageUrl();
        }
        return fallback;
    }

    // ========== 3. 订单 ==========

    private record OrderPlan(String buyer, String productName, String status,
                             int createdDaysAgo, Integer paidDaysAgo, String tracking,
                             Integer shippedDaysAgo, Integer receivedDaysAgo,
                             Integer minutesAgoCreated) {}

    private void ensureOrders(List<Product> demoProducts) {
        boolean any = userRepository.findByUsername(BUYER1).isPresent()
                && orderRepository.findByUsernameOrderByCreatedAtDesc(BUYER1).stream().anyMatch(this::isDemoOrder);
        if (any || demoProducts.isEmpty()) return;

        Map<String, Product> byName = new java.util.HashMap<>();
        demoProducts.forEach(p -> byName.put(p.getName(), p));

        // 每种状态一笔，覆盖订单列表全部筛选项
        List<OrderPlan> plans = List.of(
            new OrderPlan(BUYER1, "iPhone 13 128G 国行双卡 无拆修", "pending_payment",
                0, null, null, null, null, 5),
            new OrderPlan(BUYER1, "Redmi K60 12+256G 墨羽", "unshipped",
                0, 0, null, null, null, null),
            new OrderPlan(BUYER2, "联想小新 Air 14 锐龙版", "shipped",
                5, 5, "SF" + ThreadLocalRandom.current().nextLong(1000000000L, 9999999999L), 4, null, null),
            new OrderPlan(BUYER2, "索尼 A6000 微单套机 16-50mm", "received",
                6, 6, "YT" + ThreadLocalRandom.current().nextLong(1000000000L, 9999999999L), 5, 2, null),
            new OrderPlan(BUYER3, "JBL Flip6 蓝牙音箱 防水", "after_sale",
                7, 7, "JD" + ThreadLocalRandom.current().nextLong(1000000000L, 9999999999L), 6, 3, null),
            new OrderPlan(BUYER3, "AirPods Pro 2 国行", "completed",
                15, 15, "SF" + ThreadLocalRandom.current().nextLong(1000000000L, 9999999999L), 14, 12, null),
            new OrderPlan(BUYER1, "宜家 LED 台灯 可调光", "cancelled",
                8, null, null, null, null, null)
        );

        int n = 0;
        for (OrderPlan plan : plans) {
            Product product = byName.get(plan.productName());
            if (product == null) continue;
            if (createDemoOrder(product, plan)) n++;
        }
        if (n > 0) log.info("✓ 已创建 {} 笔演示订单（覆盖各状态）", n);
    }

    private boolean isDemoOrder(Order o) {
        return o.getUsername() != null && o.getUsername().startsWith("demo_buyer");
    }

    private boolean createDemoOrder(Product product, OrderPlan plan) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime createdAt = plan.minutesAgoCreated() != null
                ? now.minusMinutes(plan.minutesAgoCreated())
                : now.minusDays(plan.createdDaysAgo());

        Order o = new Order();
        o.setOrderNumber(generateOrderNumber());
        o.setUsername(plan.buyer());
        o.setProductId(product.getId());
        o.setSellerUsername(product.getSellerUsername());
        o.setProductName(product.getName());
        o.setProductPrice(product.getSecondHandPrice());
        o.setQuantity(1);
        o.setCategory(product.getCategory());
        o.setImageUrl(product.getFirstImage() != null ? product.getFirstImage() : "");
        o.setReceiverName("演示收货人");
        o.setReceiverPhone("13800013800");
        o.setReceiverAddress("浙江省杭州市西湖区文三路 100 号 3 幢 501 室");
        o.setPaymentMethod("balance");
        o.setStatus(plan.status());
        o.setSellerPaid(false);
        o.setCreatedAt(createdAt);
        o.setTrackingNumber(plan.tracking());
        if (plan.shippedDaysAgo() != null) o.setShippedAt(now.minusDays(plan.shippedDaysAgo()));
        if (plan.receivedDaysAgo() != null) o.setReceivedAt(now.minusDays(plan.receivedDaysAgo()));
        if ("after_sale".equals(plan.status())) {
            o.setPrevStatus("received");
            o.setAfterSaleReason("商品与描述不符，申请退款");
        }

        boolean settled = "completed".equals(plan.status());
        if (settled) o.setSellerPaid(true);

        // 占库存：待付款/在途/完成都扣；已取消不占
        boolean holdsStock = !"cancelled".equals(plan.status());
        if (holdsStock) {
            int left = Math.max(0, (product.getQuantity() != null ? product.getQuantity() : 1) - 1);
            product.setQuantity(left);
            if (left == 0) product.setStatus(ProductStatus.SOLD);
            productRepository.save(product);
        }

        Order saved = orderRepository.save(o);

        // 资金：非待付款单买家已付；已完成单卖家同时到账
        BigDecimal amount = product.getSecondHandPrice();
        boolean buyerPaid = !"pending_payment".equals(plan.status()) && !"cancelled".equals(plan.status());
        if (buyerPaid) {
            adjustBalance(plan.buyer(), amount.negate());
            walletService.add(plan.buyer(), "buy", "购买 · " + product.getName(), amount.negate());
        }
        if (settled) {
            adjustBalance(product.getSellerUsername(), amount);
            walletService.add(product.getSellerUsername(), "sell", "售出 · " + product.getName(), amount);
        }

        // 已完成的单补一条评价，店铺评分与商品评价区立刻有内容
        if (settled) {
            saveReview(saved, "东西不错，和描述一致，卖家发货也快，好评。", 5, 5, 5, 4);
        }
        return true;
    }

    private void adjustBalance(String username, BigDecimal delta) {
        userRepository.findByUsername(username).ifPresent(u -> {
            BigDecimal current = u.getBalance() != null ? u.getBalance() : BigDecimal.ZERO;
            u.setBalance(current.add(delta));
            userRepository.save(u);
        });
    }

    private String generateOrderNumber() {
        String ts = String.valueOf(System.currentTimeMillis());
        for (int i = 0; i < 10; i++) {
            String num = ts + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
            if (!orderRepository.existsByOrderNumber(num)) return num;
        }
        return ts + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    // ========== 4. 评价 ==========

    private void ensureReviews() {
        for (Order o : orderRepository.findByUsernameOrderByCreatedAtDesc(BUYER3)) {
            if ("completed".equals(o.getStatus()) && !reviewRepository.existsByOrderId(o.getId())) {
                saveReview(o, "东西不错，和描述一致，卖家发货也快，好评。", 5, 5, 5, 4);
            }
        }
        for (Order o : orderRepository.findByUsernameOrderByCreatedAtDesc(BUYER2)) {
            if ("completed".equals(o.getStatus()) && !reviewRepository.existsByOrderId(o.getId())) {
                saveReview(o, "成色比想象中好，交易过程很顺畅。", 5, 5, 4, 5);
            }
        }
    }

    private static final List<String> TOPUP_COMMENTS = List.of(
        "描述准确，包装用心，用着没问题，值得推荐。",
        "卖家很好沟通，回答问题很及时，东西也干净。",
        "性价比很高，比全新的划算，下次还会再来。",
        "发货速度一般，但商品本身没毛病，整体满意。",
        "和图片一致，没有暗病，交易顺利。",
        "成色比预期略新一点，好评。"
    );

    /** 给历史已完成订单补评价，让店铺评分与商品评价区不为空 */
    private void topUpExistingReviews() {
        int added = 0;
        for (Order o : orderRepository.findAll()) {
            if (added >= 6) break;
            if (!"completed".equals(o.getStatus())) continue;
            if (reviewRepository.existsByOrderId(o.getId())) continue;
            if (isDemoOrder(o)) continue;
            int i = added % TOPUP_COMMENTS.size();
            int rating = (i % 5 == 3) ? 4 : 5;
            saveReview(o, TOPUP_COMMENTS.get(i), rating, rating, rating, rating);
            added++;
        }
        if (added > 0) log.info("✓ 为历史订单补了 {} 条评价", added);
    }

    private void saveReview(Order o, String content, int goods, int service, int delivery, int overall) {
        if (reviewRepository.existsByOrderId(o.getId())) return;
        Review r = new Review();
        r.setOrderId(o.getId());
        r.setProductId(o.getProductId());
        r.setBuyerUsername(o.getUsername());
        r.setSellerUsername(o.getSellerUsername());
        r.setRating(overall);
        r.setRatingGoods(goods);
        r.setRatingService(service);
        r.setRatingDelivery(delivery);
        r.setContent(content);
        r.setCreatedAt(o.getReceivedAt() != null ? o.getReceivedAt().plusDays(1) : LocalDateTime.now());
        reviewRepository.save(r);

        // 订单上同步评分展示
        o.setRating(overall);
        o.setReviewContent(content);
        o.setReviewedAt(r.getCreatedAt());
        orderRepository.save(o);
    }

    // ========== 5. 举报 / 收藏 / 通知 ==========

    private void ensureExtras() {
        List<Product> products = productRepository.findAll();
        if (products.isEmpty()) return;

        // 举报：给管理端「待处理举报」留一条真实数据
        Product reported = products.stream()
                .filter(p -> ProductStatus.ACTIVE.equals(p.getStatus())
                        && p.getSellerUsername() != null && !p.getSellerUsername().startsWith("demo_"))
                .findFirst().orElse(products.get(0));
        if (!reportRepository.existsByReporterUsernameAndProductIdAndStatus(BUYER1, reported.getId(), "pending")) {
            Report report = new Report();
            report.setProductId(reported.getId());
            report.setReporterUsername(BUYER1);
            report.setProductName(reported.getName());
            report.setReason("疑似假货");
            report.setDetail("价格明显低于市场价，怀疑是仿品，麻烦平台核实一下。");
            report.setStatus("pending");
            report.setCreatedAt(LocalDateTime.now().minusHours(6));
            reportRepository.save(report);
        }

        // 收藏：让买家的收藏页不为空
        products.stream()
                .filter(p -> ProductStatus.ACTIVE.equals(p.getStatus()))
                .limit(3)
                .forEach(p -> {
                    if (!favoriteRepository.existsByUsernameAndProductId(BUYER1, p.getId())) {
                        Favorite f = new Favorite();
                        f.setUsername(BUYER1);
                        f.setProductId(p.getId());
                        favoriteRepository.save(f);
                    }
                });

        // 未读通知：铃铛角标有数字
        if (notificationRepository.findTop50ByUsernameOrderByCreatedAtDesc(BUYER1).isEmpty()) {
            push(BUYER1, "system", "欢迎使用优品汇",
                "你的演示账号已就绪，可直接浏览、下单、议价与提现。", "system", null);
            push(BUYER2, "order", "订单已发货",
                "「联想小新 Air 14」已发货，可在订单详情查看物流轨迹。", "order", null);
            push(SELLER1, "offer", "收到买家出价",
                "有买家正在和你议价，记得及时回应。", "offer", null);
        }
    }

    private void push(String username, String type, String title, String content, String refType, String refId) {
        Notification n = new Notification();
        n.setUsername(username);
        n.setType(type);
        n.setTitle(title);
        n.setContent(content);
        n.setRefType(refType);
        n.setRefId(refId);
        n.setReadFlag(false);
        n.setCreatedAt(LocalDateTime.now());
        notificationRepository.save(n);
    }
}
