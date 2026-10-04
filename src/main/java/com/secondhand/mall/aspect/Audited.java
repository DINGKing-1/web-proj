package com.secondhand.mall.aspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注管理端敏感操作，由 {@link AuditAspect} 落一条操作审计。
 * 只负责「记一笔」，不参与任何业务判断。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    /** 动作名，如「封禁用户」「商品审核通过」 */
    String action();

    /** 对象类型，如 user / product / order */
    String targetType() default "";
}
