package com.car.customer.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 评价实体（car_rental.customer_review）
 * 一表两用：
 *  1) 首页"客户真实评价"展示：读取 name/avatar/rating/content/date/carName 冗余字段
 *  2) 订单评价：通过 member_id/order_id/car_id/review_round 关联订单，支持首评(1)+追评(2)，每单最多2次
 * reviewRound: 1=首评, 2=追评；首页展示与订单评价统一存储，name/avatar/carName 为冗余快照
 */
@Data
@TableName("car_rental.customer_review")
public class Review {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 冗余：评价人昵称（首页展示用，提交评价时从 member.nickname 快照） */
    private String name;
    /** 冗余：评价人头像URL（首页展示用，提交评价时从 member.avatar 快照） */
    private String avatar;
    private Integer rating;
    private String content;
    private LocalDate date;
    /** 冗余：车辆名称（首页展示用，提交评价时从 order.car_name 快照） */
    private String carName;

    // ---------- 订单评价关联字段（v1 新增）----------
    /** 评价人会员ID（member.id） */
    private Long memberId;
    /** 关联订单ID（customer_order.id） */
    private Long orderId;
    /** 关联车辆ID（car_info.id） */
    private Long carId;
    /** 评价轮次：1=首评（订单完成后首次评价），2=追评（首评后补充评价）。每单最多2次 */
    private Integer reviewRound;
    /** 评价图片URL列表（JSON数组字符串，最多9张），无图时为 null */
    private String images;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableLogic
    private Integer isDelete;
}
