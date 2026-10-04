package com.secondhand.mall.config;

import com.secondhand.mall.exception.ForbiddenException;
import com.secondhand.mall.exception.NotFoundException;
import com.secondhand.mall.exception.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> handleNoResourceFound(NoResourceFoundException e) {
        log.debug("资源未找到: {}", e.getMessage());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("message", "资源不存在");
        return response;
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> handleNotFound(NotFoundException e) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("message", e.getMessage());
        return response;
    }

    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, Object> handleUnauthorized(UnauthorizedException e) {
        log.warn("未授权: {}", e.getMessage());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("code", "UNAUTHORIZED");
        response.put("message", e.getMessage());
        return response;
    }

    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, Object> handleForbidden(ForbiddenException e) {
        log.warn("禁止访问: {}", e.getMessage());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("code", "FORBIDDEN");
        response.put("message", e.getMessage());
        return response;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining(", "));
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("message", message.isEmpty() ? "参数校验失败" : message);
        return response;
    }

    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleRuntimeException(RuntimeException e) {
        log.warn("业务异常: {}", e.getMessage());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("message", e.getMessage());
        return response;
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleException(Exception e) {
        log.error("系统异常", e);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("message", "服务器内部错误，请稍后重试");
        return response;
    }
}
