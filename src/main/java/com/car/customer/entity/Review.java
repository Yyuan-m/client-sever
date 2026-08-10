package com.car.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

@Data
@TableName("car_rental.customer_review")
public class Review {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String avatar;
    private Integer rating;
    private String content;
    private LocalDate date;
    private String carName;

    @TableLogic
    private Integer isDelete;
}
