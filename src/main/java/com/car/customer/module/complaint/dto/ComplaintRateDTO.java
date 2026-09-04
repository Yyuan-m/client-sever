package com.car.customer.module.complaint.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 投诉满意度评分 DTO（后台处理完成后，用户对本次处理评分 1-5 星）
 */
@Data
public class ComplaintRateDTO {

    @NotNull(message = "请选择满意度评分")
    @Min(value = 1, message = "评分最低1星")
    @Max(value = 5, message = "评分最高5星")
    private Integer satisfaction;
}
