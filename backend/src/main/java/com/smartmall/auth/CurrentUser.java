package com.smartmall.auth;

public record CurrentUser(String id, String username, String role) {

    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(role);
    }
}
