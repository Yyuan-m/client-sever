package com.car.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 车辆素材图片实体（数据来源于 car_rental.car_image 表）
 * 通过 vehicle_id 关联 car_rental.car_info，category 字段进行图片分类（如"外观"、"内饰"、"宣传图"）
 */
@Data
@TableName("car_rental.car_image")
public class CarImage {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联车辆 ID（car_rental.car_info.id） */
    private Long vehicleId;

    /** 车辆名称（冗余字段，便于后台管理展示） */
    private String vehicleName;

    /** 图片分类：外观 / 内饰 / 宣传图 等 */
    private String category;

    /** 图片 URL（相对路径，如 /uploads/xxx.jpg） */
    private String url;

    /** 状态：1=有效，0=无效 */
    private Integer status;

    private LocalDateTime createdAt;
}
