package com.car.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("car_rental.carousel")
public class Carousel {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;
    private String description;
    private String imageUrl;
    private String linkUrl;
    private Integer sortOrder;
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 定时上架时间（为空表示不限制起始时间）
     */
    private LocalDateTime startTime;

    /**
     * 定时下架时间（为空表示不限制结束时间）
     */
    private LocalDateTime endTime;
}
