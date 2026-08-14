package com.car.customer.module.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 评价提交 DTO
 * 每单最多2次：首评（reviewRound=1，订单 reviewStatus=unreviewed 时提交）+ 追评（reviewRound=2，reviewStatus=reviewed 时提交）
 */
@Data
public class ReviewSubmitDTO {

    @NotNull(message = "订单ID不能为空")
    private Long orderId;

    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分最低1星")
    @Max(value = 5, message = "评分最高5星")
    private Integer rating;

    @NotBlank(message = "评价内容不能为空")
    private String content;

    /** 评价图片URL列表（JSON数组字符串，最多9张），无图时为 null */
    private String images;
}
