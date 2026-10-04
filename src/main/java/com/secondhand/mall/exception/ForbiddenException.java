package com.secondhand.mall.exception;

/** 已登录但无权限（HTTP 403），区别于未登录 401 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
