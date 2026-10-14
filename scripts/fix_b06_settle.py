from pathlib import Path

p = Path('src/main/java/com/secondhand/mall/service/OrderService.java')
t = p.read_text(encoding='utf-8')
start = t.find('    private void settleRefund(Order order) {')
# find next method comment after settleRefund
marker = '货权是否还在卖方侧'
m = t.find(marker, start)
end = t.rfind('    /**', start, m) if m > 0 else -1
if start < 0 or end < 0:
    raise SystemExit(f'anchors not found start={start} end={end} m={m}')

new = '''    /**
     * 退款/回补结算（取消、售后、管理员强退共用）。
     * 全局加锁顺序：商品 → 卖家 → 买家，与 cancelOrder/createOrder 一致，防反向加锁死锁。
     */
    private void settleRefund(Order order) {
        int refundQty = order.getQuantity() != null && order.getQuantity() > 0 ? order.getQuantity() : 1;
        boolean paid = isPaidStatus(order.getStatus());
        BigDecimal refund = paidTotal(order);

        // 1) 先锁商品（货权在卖方侧才回补；锁顺序起点）
        boolean restoreStock = stockHeldBySeller(order.getStatus());
        if (restoreStock && order.getProductId() != null) {
            productRepository.findByIdForUpdate(order.getProductId());
        }

        // 2) 再锁卖家（需扣回时）
        boolean clawback = paid && Boolean.TRUE.equals(order.getSellerPaid())
                && order.getSellerUsername() != null && !order.getSellerUsername().isEmpty();
        User seller = null;
        if (clawback) {
            seller = userRepository.findByUsernameForUpdate(order.getSellerUsername())
                    .orElseThrow(() -> new RuntimeException("卖家不存在"));
        }

        // 3) 最后锁买家并入账
        if (paid) {
            User buyer = userRepository.findByUsernameForUpdate(order.getUsername())
                    .orElseThrow(() -> new RuntimeException("用户不存在"));
            buyer.setBalance(buyer.getBalance().add(refund));
            userRepository.save(buyer);
            walletService.add(order.getUsername(), "refund",
                    "售后退款 · " + order.getProductName(), refund);

            if (seller != null) {
                seller.setBalance(seller.getBalance().subtract(refund));
                userRepository.save(seller);
                walletService.add(order.getSellerUsername(), "refund",
                        "售后扣回 · " + order.getProductName(), refund.negate());
            }
            couponService.releaseByOrder(order.getId());
        }

        // 仅货在卖方侧才回补库存：已发货/售后中货在买家或途上，回补会造出幽灵库存
        if (restoreStock) {
            stockService.restore(order.getProductId(), refundQty);
        }
    }

'''
p.write_text(t[:start] + new + t[end:], encoding='utf-8')
print('settleRefund replaced OK')
