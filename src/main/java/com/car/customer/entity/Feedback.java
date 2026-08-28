package com.car.customer.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("feedback")
public class Feedback {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联会员ID（登录态提交时自动绑定，供"我的预约"查询） */
    private Long memberId;

    private String type;
    private String name;
    private String phone;
    private String content;
    private String carType;
    private LocalDate rentDate;

    /**
     * 状态机（与后台管理系统对齐）：
     * pending 待处理（用户提交/后台待跟进）
     * handled 已处理（后台标记完成，附处理备注/处理人/处理时间）
     * cancelled 已取消（用户自主取消，仅 pending 状态可取消）
     */
    private String status;

    /** 处理备注（后台处理说明/沟通结果，或用户取消轨迹） */
    private String remark;

    /** 处理人（后台管理员账号名） */
    private String handler;

    /** 最近处理时间 */
    private LocalDateTime processTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
