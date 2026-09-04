package com.car.customer.module.complaint.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 投诉提交 DTO
 * 投诉类型来自字典 complaint_type；订单号选填（从订单详情进入时自动带出）
 */
@Data
public class ComplaintSubmitDTO {

    /** 关联订单号（必填，从用户自己的订单中选择） */
    @NotBlank(message = "请选择关联订单")
    @Size(max = 50, message = "订单号格式不正确")
    private String orderNo;

    /** 投诉类型（字典 complaint_type 的 dictValue，必填） */
    @NotBlank(message = "请选择投诉类型")
    private String type;

    /** 投诉描述（必填，最长1000字） */
    @NotBlank(message = "请填写投诉描述")
    @Size(max = 1000, message = "投诉描述不能超过1000字")
    private String description;

    /** 投诉凭证图片URL列表（相对路径，最多9张） */
    @Size(max = 9, message = "凭证图片最多9张")
    private List<String> images;
}
