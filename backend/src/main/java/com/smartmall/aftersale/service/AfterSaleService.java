package com.smartmall.aftersale.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartmall.aftersale.AfterSaleStatus;
import com.smartmall.aftersale.dto.AfterSaleVO;
import com.smartmall.aftersale.dto.CreateAfterSaleRequest;
import com.smartmall.aftersale.dto.HandleAfterSaleRequest;
import com.smartmall.aftersale.entity.AfterSale;
import com.smartmall.aftersale.mapper.AfterSaleMapper;
import com.smartmall.auth.AuthContext;
import com.smartmall.common.BizException;
import com.smartmall.common.Ids;
import com.smartmall.common.PageResult;
import com.smartmall.order.entity.Order;
import com.smartmall.order.mapper.OrderMapper;
import com.smartmall.user.entity.SysUser;
import com.smartmall.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AfterSaleService {

    private final AfterSaleMapper afterSaleMapper;
    private final OrderMapper orderMapper;
    private final SysUserMapper userMapper;

    @Transactional
    public AfterSaleVO create(CreateAfterSaleRequest request) {
        String userId = AuthContext.userId();
        Order order = orderMapper.selectById(request.getOrderId());
        if (order == null || !order.getUserId().equals(userId)) {
            throw BizException.notFound("订单不存在");
        }
        if (!List.of("PAID", "SHIPPED", "COMPLETED").contains(order.getStatus())) {
            throw new BizException("当前订单状态不支持申请售后");
        }
        Long exists = afterSaleMapper.selectCount(new LambdaQueryWrapper<AfterSale>()
                .eq(AfterSale::getOrderId, order.getId()));
        if (exists != null && exists > 0) {
            throw new BizException("该订单已提交过售后申请");
        }
        AfterSale afterSale = new AfterSale();
        afterSale.setId(Ids.next());
        afterSale.setOrderId(order.getId());
        afterSale.setUserId(userId);
        afterSale.setType(request.getType());
        afterSale.setReason(request.getReason());
        afterSale.setStatus(AfterSaleStatus.PENDING);
        afterSale.setCreatedAt(LocalDateTime.now());
        afterSaleMapper.insert(afterSale);
        return toVO(afterSale, order, userMapper.selectById(userId));
    }

    public List<AfterSaleVO> mine() {
        String userId = AuthContext.userId();
        List<AfterSale> list = afterSaleMapper.selectList(new LambdaQueryWrapper<AfterSale>()
                .eq(AfterSale::getUserId, userId)
                .orderByDesc(AfterSale::getCreatedAt));
        if (list.isEmpty()) {
            return List.of();
        }
        Map<String, Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                        .in(Order::getId, list.stream().map(AfterSale::getOrderId).toList()))
                .stream().collect(Collectors.toMap(Order::getId, Function.identity(), (a, b) -> a));
        SysUser user = userMapper.selectById(userId);
        return list.stream().map(item -> toVO(item, orders.get(item.getOrderId()), user)).toList();
    }

    public List<AfterSaleVO> byOrder(String orderId) {
        String userId = AuthContext.userId();
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw BizException.notFound("订单不存在");
        }
        return afterSaleMapper.selectList(new LambdaQueryWrapper<AfterSale>()
                        .eq(AfterSale::getOrderId, orderId)
                        .orderByDesc(AfterSale::getCreatedAt))
                .stream().map(item -> toVO(item, order, userMapper.selectById(userId))).toList();
    }

    public PageResult<AfterSaleVO> adminList(String status, long pageNo, long size) {
        Page<AfterSale> page = afterSaleMapper.selectPage(
                new Page<>(Math.max(1, pageNo), Math.min(50, Math.max(1, size))),
                new LambdaQueryWrapper<AfterSale>()
                        .eq(StringUtils.hasText(status), AfterSale::getStatus, status)
                        .orderByDesc(AfterSale::getCreatedAt));
        List<AfterSale> records = page.getRecords();
        Map<String, Order> orders = records.isEmpty() ? Map.of()
                : orderMapper.selectList(new LambdaQueryWrapper<Order>()
                        .in(Order::getId, records.stream().map(AfterSale::getOrderId).toList()))
                .stream().collect(Collectors.toMap(Order::getId, Function.identity(), (a, b) -> a));
        Map<String, SysUser> users = records.isEmpty() ? Map.of()
                : userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .in(SysUser::getId, records.stream().map(AfterSale::getUserId).distinct().toList()))
                .stream().collect(Collectors.toMap(SysUser::getId, Function.identity(), (a, b) -> a));
        List<AfterSaleVO> vos = records.stream()
                .map(item -> toVO(item, orders.get(item.getOrderId()), users.get(item.getUserId())))
                .toList();
        return PageResult.of(vos, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Transactional
    public AfterSaleVO handle(String id, HandleAfterSaleRequest request) {
        AfterSale afterSale = afterSaleMapper.selectById(id);
        if (afterSale == null) {
            throw BizException.notFound("售后申请不存在");
        }
        if (!AfterSaleStatus.PENDING.equals(afterSale.getStatus())) {
            throw new BizException("该售后申请已处理");
        }
        afterSale.setStatus(request.getStatus());
        afterSale.setAdminNote(request.getAdminNote());
        afterSale.setHandledAt(LocalDateTime.now());
        afterSaleMapper.updateById(afterSale);
        return toVO(afterSale, orderMapper.selectById(afterSale.getOrderId()),
                userMapper.selectById(afterSale.getUserId()));
    }

    private AfterSaleVO toVO(AfterSale afterSale, Order order, SysUser user) {
        AfterSaleVO vo = new AfterSaleVO();
        vo.setId(afterSale.getId());
        vo.setOrderId(afterSale.getOrderId());
        vo.setOrderNo(order == null ? null : order.getOrderNo());
        vo.setOrderAmount(order == null ? null : order.getTotalAmount());
        vo.setUserId(afterSale.getUserId());
        vo.setUsername(user == null ? afterSale.getUserId() : user.getUsername());
        vo.setType(afterSale.getType());
        vo.setTypeLabel(AfterSaleStatus.typeLabel(afterSale.getType()));
        vo.setReason(afterSale.getReason());
        vo.setStatus(afterSale.getStatus());
        vo.setStatusLabel(AfterSaleStatus.label(afterSale.getStatus()));
        vo.setAdminNote(afterSale.getAdminNote());
        vo.setCreatedAt(afterSale.getCreatedAt());
        vo.setHandledAt(afterSale.getHandledAt());
        return vo;
    }
}
