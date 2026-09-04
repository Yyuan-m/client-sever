package com.car.customer.module.complaint.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 我的投诉记录 VO（含类型/状态中文名与凭证图片数组）
 */
@Data
public class ComplaintVO {

    private Long id;

    /** 工单编号 */
    private String ticketNo;

    /** 关联订单号 */
    private String orderNo;

    /** 客户姓名 */
    private String customerName;

    /** 投诉类型（dictValue） */
    private String type;

    /** 投诉类型中文名 */
    private String typeName;

    /** 投诉描述 */
    private String description;

    /** 投诉凭证图片（相对路径数组） */
    private List<String> images;

    /** 优先级 */
    private String priority;

    /** 状态（dictValue） */
    private String status;

    /** 状态中文名 */
    private String statusName;

    /** 处理方案（已处理/已解决后后台填写） */
    private String solution;

    /** 处理人员（后台处理人账号） */
    private String assignee;

    /** 满意度评分：0=未评，1-5星（后台处理完成后由用户评分） */
    private Integer satisfaction;

    /** 提交时间 */
    private LocalDateTime createdAt;
}
