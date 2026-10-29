package com.smartmall.notification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartmall.auth.AuthContext;
import com.smartmall.common.BizException;
import com.smartmall.common.PageParams;
import com.smartmall.common.PageResult;
import com.smartmall.notification.entity.Notification;
import com.smartmall.notification.mapper.NotificationMapper;
import com.smartmall.user.entity.SysUser;
import com.smartmall.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationMapper notificationMapper;
    private final SysUserMapper userMapper;

    public void notify(String userId, String type, String title, String content, String link) {
        if (userId == null) {
            return;
        }
        try {
            Notification notification = new Notification();
            notification.setUserId(userId);
            notification.setType(type);
            notification.setTitle(title);
            notification.setContent(content);
            notification.setLink(link);
            notification.setReadStatus(0);
            notification.setCreatedAt(LocalDateTime.now());
            notificationMapper.insert(notification);
        } catch (Exception e) {
            log.warn("站内通知写入失败: {}", e.getMessage());
        }
    }

    public void notifyAdmins(String type, String title, String content, String link) {
        List<SysUser> admins = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getRole, "ADMIN")
                .eq(SysUser::getStatus, 1));
        for (SysUser admin : admins) {
            notify(admin.getId(), type, title, content, link);
        }
    }

    public PageResult<Notification> mine(long pageNo, long size) {
        long[] checked = PageParams.check(pageNo, size, 50);
        pageNo = checked[0];
        size = checked[1];
        Page<Notification> page = notificationMapper.selectPage(
                new Page<>(pageNo, size),
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, AuthContext.userId())
                        .orderByAsc(Notification::getReadStatus)
                        .orderByDesc(Notification::getCreatedAt));
        return PageResult.of(page);
    }

    public long unreadCount() {
        Long count = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, AuthContext.userId())
                .eq(Notification::getReadStatus, 0));
        return count == null ? 0 : count;
    }

    @Transactional
    public void markRead(Long id) {
        Notification notification = notificationMapper.selectById(id);
        if (notification == null || !notification.getUserId().equals(AuthContext.userId())) {
            throw BizException.notFound("通知不存在");
        }
        if (notification.getReadStatus() == null || notification.getReadStatus() == 0) {
            notification.setReadStatus(1);
            notificationMapper.updateById(notification);
        }
    }

    @Transactional
    public void markAllRead() {
        List<Notification> unread = notificationMapper.selectList(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, AuthContext.userId())
                .eq(Notification::getReadStatus, 0));
        for (Notification notification : unread) {
            notification.setReadStatus(1);
            notificationMapper.updateById(notification);
        }
    }
}
