package com.smartmall.auth;

import com.smartmall.common.BizException;

public final class AuthContext {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    public static CurrentUser get() {
        return HOLDER.get();
    }

    public static CurrentUser require() {
        CurrentUser user = HOLDER.get();
        if (user == null) {
            throw BizException.unauthorized("请先登录");
        }
        return user;
    }

    public static String userId() {
        return require().id();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
