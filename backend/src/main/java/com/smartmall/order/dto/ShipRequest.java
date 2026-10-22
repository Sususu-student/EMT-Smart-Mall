package com.smartmall.order.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ShipRequest {

    @Size(max = 50, message = "承运商名称过长")
    private String carrier;

    @Size(max = 64, message = "运单号过长")
    private String trackingNo;
}
