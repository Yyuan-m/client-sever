package com.car.customer.module.cart.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AddCartDTO {

    @NotNull(message = "车辆ID不能为空")
    private Long carId;

    private String startDate;

    private String endDate;

    private Integer days;

    private Integer quantity;
}
