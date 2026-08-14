package com.car.customer.module.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateOrderDTO {

    private String city;
    private String store;

    @NotBlank(message = "联系人不能为空")
    private String name;

    @NotBlank(message = "联系电话不能为空")
    private String phone;

    /**
     * 用户优惠券实例ID列表（member_coupon.id，v3 支持多张可叠加券）
     * 兼容旧字段 couponUserId：若 couponUserIds 为空则回退使用 couponUserId
     */
    private List<Long> couponUserIds;

    /** 旧字段兼容：单券下单时使用（v3 推荐用 couponUserIds） */
    private Long couponUserId;

    @NotEmpty(message = "下单车辆不能为空")
    private List<CartItemDTO> items;

    @Data
    public static class CartItemDTO {
        private Long carId;
        private String carName;
        private String cover;
        private String startDate;
        private String endDate;
        private Integer days;
        private BigDecimal dailyPrice;
    }
}
