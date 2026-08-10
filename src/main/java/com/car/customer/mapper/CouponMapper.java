package com.car.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.car.customer.entity.Coupon;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface CouponMapper extends BaseMapper<Coupon> {

    /**
     * 查询券关联的车辆ID列表
     */
    @Select("SELECT car_id FROM car_rental.coupon_car WHERE coupon_id = #{couponId}")
    List<Long> selectCarIdsByCouponId(@Param("couponId") Long couponId);

    /**
     * 查询券关联的车辆名称列表（用于列表展示）
     */
    @Select("SELECT ci.name FROM car_rental.coupon_car cc " +
            "LEFT JOIN car_rental.car_info ci ON cc.car_id = ci.id " +
            "WHERE cc.coupon_id = #{couponId} AND ci.is_delete = 0")
    List<String> selectCarNamesByCouponId(@Param("couponId") Long couponId);

    /**
     * 原子扣减库存（领取时：received_count+1，需校验剩余量）
     * @return 影响行数，0 表示库存不足或券不存在
     */
    @Update("UPDATE car_rental.coupon SET received_count = received_count + 1, version = version + 1 " +
            "WHERE id = #{couponId} AND (total_count = -1 OR received_count < total_count) AND is_delete = 0")
    int incrReceivedCount(@Param("couponId") Long couponId);

    /**
     * 原子扣减核销数（核销时：used_count+1）
     */
    @Update("UPDATE car_rental.coupon SET used_count = used_count + 1, version = version + 1 " +
            "WHERE id = #{couponId} AND used_count < received_count AND is_delete = 0")
    int incrUsedCount(@Param("couponId") Long couponId);

    /**
     * 回滚领取数量（领取失败/取消时回退）
     */
    @Update("UPDATE car_rental.coupon SET received_count = GREATEST(received_count - 1, 0), version = version + 1 " +
            "WHERE id = #{couponId}")
    int decrReceivedCount(@Param("couponId") Long couponId);

    /**
     * 回滚核销数量（取消订单时回退）
     */
    @Update("UPDATE car_rental.coupon SET used_count = GREATEST(used_count - 1, 0), version = version + 1 " +
            "WHERE id = #{couponId}")
    int decrUsedCount(@Param("couponId") Long couponId);
}
