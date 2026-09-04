package com.car.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 售后投诉/工单实体（跨库 car_rental.after_sales_complaint）
 * 与后台管理系统共用同一张表，C 端提交后进入后台售后工单列表处理
 */
@Data
@TableName("car_rental.after_sales_complaint")
public class AfterSalesComplaint {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工单编号（唯一） */
    private String ticketNo;

    /** 关联订单ID */
    private Long orderId;

    /** 关联订单号 */
    private String orderNo;

    /** 关联会员ID（C端提交时记录，用于"我的投诉"查询） */
    private Long memberId;

    /** 客户姓名 */
    private String customerName;

    /** 投诉类型（字典 complaint_type 的 dictValue） */
    private String type;

    /** 类型名称（字典 label 快照） */
    private String typeName;

    /** 投诉描述 */
    private String description;

    /** 投诉凭证图片（JSON数组字符串，如 ["/uploads/xxx.jpg"]） */
    private String images;

    /** 优先级 normal/high/urgent */
    private String priority;

    /** 状态 pending/processing/resolved/rejected */
    private String status;

    /** 处理人 */
    private String assignee;

    /** 解决方案 */
    private String solution;

    /** 满意度 1-5，0 未评 */
    private Integer satisfaction;

    private LocalDateTime createdAt;

    private LocalDateTime resolvedAt;
}
