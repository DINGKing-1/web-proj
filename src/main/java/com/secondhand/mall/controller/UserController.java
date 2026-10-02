package com.secondhand.mall.controller;

import com.secondhand.mall.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/avatar")
    public ResponseEntity<?> uploadAvatar(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        try {
            HttpSession session = request.getSession();
            String username = (String) session.getAttribute("username");
            if (username == null) {
                return ResponseEntity.status(401).body(Map.of("message", "未登录"));
            }

            String avatarUrl = userService.uploadAvatar(username, file);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "avatar", avatarUrl
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }
}
