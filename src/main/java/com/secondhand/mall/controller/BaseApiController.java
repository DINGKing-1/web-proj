package com.secondhand.mall.controller;

import com.secondhand.mall.dto.ApiResponse;
import com.secondhand.mall.exception.UnauthorizedException;
import jakarta.servlet.http.HttpSession;

/**
 * Controller 基类：抽取公共方法，消除重复代码。
 * 所有需要登录校验的 Controller 继承此类。
 */
public abstract class BaseApiController {

    /**
     * 获取当前登录用户名，未登录则抛出 UnauthorizedException
     */
    protected String getLoginUsername(HttpSession session) {
        Object username = session.getAttribute("username");
        if (username == null) {
            throw new UnauthorizedException("请先登录");
        }
        return username.toString();
    }

    protected ApiResponse ok(String message) {
        return ApiResponse.ok(message);
    }

    protected ApiResponse fail(String message) {
        return ApiResponse.fail(message);
    }
}
