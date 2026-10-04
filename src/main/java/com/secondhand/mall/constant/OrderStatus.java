package com.secondhand.mall.constant;

/**
 * 订单状态与合法流转，供 OrderService / 前端约定共用。
 */
public final class OrderStatus {

    /** 待付款：下单占库存但未扣款，超时自动关单 */
    public static final String PENDING_PAYMENT = "pending_payment";
    public static final String UNSHIPPED = "unshipped";
    public static final String SHIPPED = "shipped";
    public static final String RECEIVED = "received";
    public static final String AFTER_SALE = "after_sale";
    public static final String COMPLETED = "completed";
    public static final String CANCELLED = "cancelled";

    /** 售后：卖家已同意退货，等待买家寄回 */
    public static final String RETURN_SHIPPING = "return_shipping";
    /** 售后：买家已寄回，等待卖家确认收货并退款 */
    public static final String RETURN_SENT = "return_sent";
    /** 售后：卖家确认收到退货，钱已退回买家 */
    public static final String REFUNDED = "refunded";

    private OrderStatus() {
    }
}
