package com.car.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.car.customer.entity.CarImage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 车辆素材图片 Mapper（数据来自 car_rental.car_image 表）
 */
public interface CarImageMapper extends BaseMapper<CarImage> {

    /**
     * 按车辆 ID 查询所有有效素材图片（按分类、创建时间排序）
     * @param vehicleId 车辆 ID（car_rental.car_info.id）
     * @return 图片列表
     */
    @Select("""
            SELECT id, vehicle_id, vehicle_name, category, url, status, created_at
            FROM car_rental.car_image
            WHERE vehicle_id = #{vehicleId} AND status = 1
            ORDER BY category, created_at
            """)
    List<CarImage> selectByVehicleId(@Param("vehicleId") Long vehicleId);
}
