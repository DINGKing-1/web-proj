package com.secondhand.mall.dto;

import java.util.HashMap;
import java.util.Map;

public class ApiResponse {
    private boolean success;
    private String message;
    private Map<String, Object> data;

    private ApiResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
        this.data = new HashMap<>();
    }

    public static ApiResponse ok(String message) {
        return new ApiResponse(true, message);
    }

    public static ApiResponse fail(String message) {
        return new ApiResponse(false, message);
    }

    public ApiResponse put(String key, Object value) {
        this.data.put(key, value);
        return this;
    }

    public ApiResponse putAll(Map<String, Object> entries) {
        this.data.putAll(entries);
        return this;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("success", success);
        map.put("message", message);
        map.putAll(data);
        return map;
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public Map<String, Object> getData() { return data; }
}
