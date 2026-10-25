package com.smartmall.auth;

public record CurrentUser(String id, String username, String role) {

    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(role);
    }

    /** 卖家（可以发布和管理自己的商品） */
    public boolean isSeller() {
        return "SELLER".equalsIgnoreCase(role);
    }

    /** 卖家或管理员都具备商品管理能力，但管理员可管理全部商品 */
    public boolean canManageProducts() {
        return isSeller() || isAdmin();
    }
}
