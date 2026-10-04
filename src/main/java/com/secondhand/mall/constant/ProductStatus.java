package com.secondhand.mall.constant;

/**
 * 商品状态常量：避免 active/sold/deleted/inactive 字符串散落各处写错。
 */
public final class ProductStatus {

    public static final String ACTIVE = "active";
    public static final String SOLD = "sold";
    public static final String INACTIVE = "inactive";
    public static final String DELETED = "deleted";
    /** 已提交，等待管理员审核（前台不可见） */
    public static final String PENDING = "pending";
    /** 审核驳回，卖家可修改后重新提交 */
    public static final String REJECTED = "rejected";

    private ProductStatus() {
    }
}
