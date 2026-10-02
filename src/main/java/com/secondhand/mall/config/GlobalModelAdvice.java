package com.secondhand.mall.config;

import jakarta.servlet.http.HttpSession;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 全局模型增强：向所有 Thymeleaf 页面注入登录状态，
 * 让服务端直接渲染正确的导航栏，彻底消除页面切换时的闪烁。
 */
@ControllerAdvice
public class GlobalModelAdvice {

    @ModelAttribute
    public void addSessionUser(Model model, HttpSession session) {
        if (session == null) {
            model.addAttribute("isLoggedIn", false);
            return;
        }
        String username = (String) session.getAttribute("username");
        if (username != null) {
            model.addAttribute("isLoggedIn", true);
            model.addAttribute("sessionUsername", username);
            model.addAttribute("sessionNickname", session.getAttribute("nickname"));
            model.addAttribute("sessionRole", session.getAttribute("role"));
        } else {
            model.addAttribute("isLoggedIn", false);
        }
    }
}
