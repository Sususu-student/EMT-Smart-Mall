package com.smartmall.auth;

import com.smartmall.common.BizException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtService jwtService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        boolean publicApi = AnnotatedElementUtils.hasAnnotation(method.getMethod(), PublicApi.class)
                || AnnotatedElementUtils.hasAnnotation(method.getBeanType(), PublicApi.class);

        String header = request.getHeader("Authorization");
        CurrentUser user = null;
        if (header != null && header.startsWith("Bearer ")) {
            try {
                user = jwtService.parse(header.substring(7).trim());
                AuthContext.set(user);
            } catch (JwtException | IllegalArgumentException e) {
                if (!publicApi) {
                    throw BizException.unauthorized("登录状态已失效，请重新登录");
                }
            }
        }

        if (!publicApi && user == null) {
            throw BizException.unauthorized("请先登录后再操作");
        }

        boolean adminOnly = AnnotatedElementUtils.hasAnnotation(method.getMethod(), AdminOnly.class)
                || AnnotatedElementUtils.hasAnnotation(method.getBeanType(), AdminOnly.class)
                || request.getRequestURI().startsWith("/api/admin");
        if (adminOnly && (user == null || !user.isAdmin())) {
            throw BizException.forbidden("需要管理员权限");
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContext.clear();
    }
}
