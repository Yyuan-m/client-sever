package com.car.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公告实体（数据来源于 car_rental.announcement 表）
 * priority 为字符串：high(高) / normal(中) / low(低)
 * status：1=已发布 0=隐藏
 */
@Data
@TableName("car_rental.announcement")
public class Announcement {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String content;

    /**
     * 优先级：high / normal / low
     */
    private String priority;

    /**
     * 1=已发布 0=隐藏
     */
    private Integer status;

    @TableLogic
    private Integer isDelete;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
