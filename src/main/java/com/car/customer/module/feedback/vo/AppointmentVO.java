package com.car.customer.module.feedback.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 我的预约项 VO（C 端展示）
 */
@Data
public class AppointmentVO {

    private Long id;

    /** 意向车型 */
    private String carType;

    /** 取车日期 */
    private LocalDate rentDate;

    /** 联系人姓名（脱敏） */
    private String name;

    /** 联系手机号（脱敏） */
    private String phone;

    /** 留言内容 */
    private String content;

    /** 状态：pending 待处理 / handled 已处理 / cancelled 已取消 */
    private String status;

    /** 状态中文名（后端统一出口，避免前端维护映射） */
    private String statusName;

    /** 处理备注（后台处理说明/沟通结果，或用户取消轨迹） */
    private String remark;

    /** 处理人（后台管理员账号名） */
    private String handler;

    /** 提交时间 */
    private LocalDateTime createTime;

    /** 最近处理时间 */
    private LocalDateTime processTime;

    /** 是否可取消（仅待处理/已确认状态） */
    private Boolean cancellable;
}
