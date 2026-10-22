package com.smartmall.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartmall.payment.entity.PaymentRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PaymentRecordMapper extends BaseMapper<PaymentRecord> {
}
