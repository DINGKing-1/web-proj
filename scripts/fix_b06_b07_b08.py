from pathlib import Path

p = Path('src/main/java/com/secondhand/mall/service/OrderService.java')
t = p.read_text(encoding='utf-8')

# ---- cancelOrder ----
start = t.find('    public Order cancelOrder(Long id, String currentUsername) {')
end = t.find('    /**\n     * 买家申请售后', start)
if start < 0 or end < 0:
    raise SystemExit(f'cancelOrder anchors start={start} end={end}')

new_cancel = '''    public Order cancelOrder(Long id, String currentUsername) {
        // 订单行锁：防并发双击取消导致双重退款
        Order order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RuntimeException("订单不存在"));

        if (!order.getUsername().equals(currentUsername)) {
            throw new RuntimeException("只有买家可以取消订单");
        }

        String st = order.getStatus();
        if (!OrderStatus.UNSHIPPED.equals(st) && !OrderStatus.PENDING_PAYMENT.equals(st)) {
            throw new RuntimeException("只能取消待付款或待发货的订单");
        }

        int refundQty = order.getQuantity() != null && order.getQuantity() > 0 ? order.getQuantity() : 1;
        BigDecimal refund = paidTotal(order);

        // 全局加锁顺序：商品 → 卖家 → 买家（与 settleRefund 一致，防死锁）
        Product product = productRepository.findByIdForUpdate(order.getProductId()).orElse(null);

        boolean clawback = OrderStatus.UNSHIPPED.equals(st)
                && Boolean.TRUE.equals(order.getSellerPaid())
                && order.getSellerUsername() != null && !order.getSellerUsername().isEmpty();
        User seller = null;
        if (clawback) {
            seller = userRepository.findByUsernameForUpdate(order.getSellerUsername())
                    .orElseThrow(() -> new RuntimeException("卖家不存在"));
        }

        // 仅已支付订单才退款；待付款订单未扣款，直接关单即可
        if (OrderStatus.UNSHIPPED.equals(st)) {
            User buyer = userRepository.findByUsernameForUpdate(order.getUsername())
                    .orElseThrow(() -> new RuntimeException("用户不存在"));
            buyer.setBalance(buyer.getBalance().add(refund));
            userRepository.save(buyer);
            walletService.add(order.getUsername(), "refund", "取消退款 · " + order.getProductName(), refund);
            couponService.releaseByOrder(order.getId());

            // 资金托管下卖家尚未收款，无需扣回；兼容旧数据（已打款则扣回）
            if (seller != null) {
                seller.setBalance(seller.getBalance().subtract(refund));
                userRepository.save(seller);
                walletService.add(order.getSellerUsername(), "refund", "取消扣回 · " + order.getProductName(), refund.negate());
            }
        }

        // 按订单购买数量恢复商品库存（StockService 行锁 + 版本号）
        if (product != null) {
            stockService.restore(product.getId(), refundQty);
        }

        order.setStatus(OrderStatus.CANCELLED);
        return orderRepository.save(order);
    }

'''
t = t[:start] + new_cancel + t[end:]

# ---- adminForceCancel ----
start = t.find('    public Order adminForceCancel(Long id) {')
end = t.find('    /**\n     * 是否已实际扣款', start)
if start < 0 or end < 0:
    raise SystemExit(f'adminForceCancel anchors start={start} end={end}')

new_force = '''    /**
     * 管理员强制取消并退款。
     * 仅待付款/待发货允许；已发货/已收货/售后中禁止直接退全款（须走售后退货流程），防白拿资损。
     */
    @Audited(action = "强制取消订单", targetType = "order")
    @Transactional
    public Order adminForceCancel(Long id, String operator, String reason) {
        Order order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RuntimeException("订单不存在"));
        String st = order.getStatus();
        if (OrderStatus.COMPLETED.equals(st) || OrderStatus.CANCELLED.equals(st)
                || OrderStatus.REFUNDED.equals(st)) {
            throw new RuntimeException("订单已完结，无法强制取消");
        }
        // B-07：已履约订单禁止强退全款
        if (OrderStatus.SHIPPED.equals(st) || OrderStatus.RECEIVED.equals(st)
                || OrderStatus.AFTER_SALE.equals(st) || OrderStatus.RETURN_SHIPPING.equals(st)
                || OrderStatus.RETURN_SENT.equals(st)) {
            throw new RuntimeException("已发货/已收货订单禁止直接强制退款，请走售后退货流程后再处理");
        }
        if (reason == null || reason.isBlank()) {
            throw new RuntimeException("请填写强制取消原因");
        }
        settleRefund(order);
        order.setStatus(OrderStatus.CANCELLED);
        order.setAdminCancelReason("操作人:" + operator + " 原因:" + reason.trim());
        return orderRepository.save(order);
    }

'''
t = t[:start] + new_force + t[end:]

# ---- releaseToSeller ----
start = t.find('    public void releaseToSeller(Order order) {')
end = t.find('    public List<Order> getBuyerOrders', start)
if start < 0 or end < 0:
    raise SystemExit(f'releaseToSeller anchors start={start} end={end}')

new_release = '''    /**
     * 托管货款打给卖家。卖家不存在/已注销时绝不能标 sellerPaid，
     * 记「打款异常」转人工队列，保证对账一致。
     */
    @Transactional
    public void releaseToSeller(Order order) {
        if (Boolean.TRUE.equals(order.getSellerPaid())) {
            return;
        }
        String sellerName = order.getSellerUsername();
        if (sellerName == null || sellerName.isBlank()) {
            markPayException(order, "订单缺少卖家账号，货款暂扣待人工处理");
            return;
        }
        int qty = order.getQuantity() != null && order.getQuantity() > 0 ? order.getQuantity() : 1;
        BigDecimal amount = paidTotal(order);

        // 同一事务内：锁卖家 → 加余额 → 标 sellerPaid
        User seller = userRepository.findByUsernameForUpdate(sellerName).orElse(null);
        if (seller == null || Boolean.TRUE.equals(seller.getDeleted())) {
            markPayException(order, "卖家账号不存在或已注销，货款暂扣待人工处理");
            return;
        }
        seller.setBalance(seller.getBalance().add(amount));
        userRepository.save(seller);
        walletService.add(sellerName, "sell", "售出 · " + order.getProductName(), amount);
        order.setSellerPaid(true);
    }

    /** 打款异常：标记并告警，不改 sellerPaid，由人工队列处理 */
    private void markPayException(Order order, String reason) {
        order.setPayException(true);
        order.setPayExceptionReason(reason);
        order.setSellerPaid(false);
        log.warn("[PayException] 订单 {} 打款异常: {}", order.getId(), reason);
        // 通知平台管理员人工介入
        try {
            notificationService.push("admin", "order", "订单打款异常",
                    "订单「" + order.getProductName() + "」" + reason + "，请人工处理",
                    "order", String.valueOf(order.getId()));
        } catch (Exception ignored) {
            // 通知失败不阻断结算，日志已记录
        }
    }

'''
t = t[:start] + new_release + t[end:]

p.write_text(t, encoding='utf-8')
print('cancelOrder / adminForceCancel / releaseToSeller replaced OK')
