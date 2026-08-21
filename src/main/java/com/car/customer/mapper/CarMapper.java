package com.car.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.car.customer.entity.Car;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

/**
 * 车辆 Mapper（全部数据来自 car_rental.car_info，图片取自 images 字段）
 * car_rental_customer.car 表已删除，BaseMapper 方法直接查 car_rental.car_info。
 * type/status 在 SQL 中用 CASE WHEN 转换为前端期望的英文值/中文名称。
 */
public interface CarMapper extends BaseMapper<Car> {

    /**
     * 分页查询车辆列表（支持类型/关键字/价格区间/状态/排序）
     * type/status 在 SQL 中做中文↔英文映射
     */
    @Select("""
            <script>
            SELECT
                ci.id, ci.name, ci.brand, ci.series,
                CASE ci.type
                    WHEN '超级跑车' THEN 'sports'
                    WHEN '豪华轿车' THEN 'sedan'
                    WHEN '豪华SUV'   THEN 'suv'
                    WHEN '豪华MPV'   THEN 'mpv'
                    WHEN '高端新能源' THEN 'energy'
                    ELSE 'suv'
                END AS type,
                CASE ci.type
                    WHEN '超级跑车' THEN '跑车'
                    WHEN '豪华轿车' THEN '轿车'
                    WHEN '豪华SUV'   THEN 'SUV'
                    WHEN '豪华MPV'   THEN '商务车'
                    WHEN '高端新能源' THEN '新能源'
                    WHEN '定制改装车' THEN '改装车'
                    ELSE ci.type
                END AS type_name,
                ci.daily_price,
                ci.min_rent_days,
                ci.max_rent_days,
                ci.weekly_discount,
                ci.monthly_discount,
                ci.holiday_surcharge,
                CASE ci.status
                    WHEN 'idle'        THEN 'available'
                    WHEN 'returning'   THEN 'available'
                    WHEN 'rented'      THEN 'rented'
                    WHEN 'reserved'    THEN 'maintenance'
                    WHEN 'maintenance' THEN 'maintenance'
                    ELSE 'available'
                END AS status,
                CASE ci.status
                    WHEN 'idle'        THEN '可租'
                    WHEN 'returning'   THEN '可租'
                    WHEN 'rented'      THEN '已出租'
                    WHEN 'reserved'    THEN '维修中'
                    WHEN 'maintenance' THEN '维修中'
                    ELSE '可租'
                END AS status_name,
                ci.images,
                ci.tags, ci.is_hot, ci.is_recommend, ci.seats, ci.displacement, ci.color,
                YEAR(ci.registration_date) AS year, ci.mileage, ci.description,
                5.0 AS rating,
                -- 已租次数：status 为 renting/completed 的订单数
                (SELECT COUNT(*) FROM car_rental.customer_order co
                    WHERE co.car_id = ci.id AND co.is_delete = 0 AND co.status IN ('renting','completed')) AS rent_count,
                -- 累计已租天数：所有 renting/completed 订单 days 之和（无订单时为 0）
                COALESCE((SELECT SUM(co.days) FROM car_rental.customer_order co
                    WHERE co.car_id = ci.id AND co.is_delete = 0 AND co.status IN ('renting','completed')), 0) AS rent_days
            FROM car_rental.car_info ci
            WHERE ci.is_delete = 0
            <if test="type != null and type != '' and type != 'all'">
                AND ci.type = CASE #{type}
                    WHEN 'sports' THEN '超级跑车'
                    WHEN 'sedan'  THEN '豪华轿车'
                    WHEN 'suv'    THEN '豪华SUV'
                    WHEN 'mpv'    THEN '豪华MPV'
                    WHEN 'energy' THEN '高端新能源'
                    ELSE ci.type
                END
            </if>
            <if test="keyword != null and keyword != ''">
                AND ci.name LIKE CONCAT('%', #{keyword}, '%')
            </if>
            <if test="minPrice != null">
                AND ci.daily_price &gt;= #{minPrice}
            </if>
            <if test="maxPrice != null">
                AND ci.daily_price &lt;= #{maxPrice}
            </if>
            <if test="status != null and status != ''">
                <choose>
                    <when test="status == 'available'.toString()">
                        AND ci.status IN ('idle', 'returning')
                    </when>
                    <when test="status == 'rented'.toString()">
                        AND ci.status = 'rented'
                    </when>
                    <when test="status == 'maintenance'.toString()">
                        AND ci.status IN ('reserved', 'maintenance')
                    </when>
                </choose>
            </if>
            ORDER BY
            <choose>
                <when test="sort == 'price-asc'.toString()">ci.daily_price ASC</when>
                <when test="sort == 'price-desc'.toString()">ci.daily_price DESC</when>
                <when test="sort == 'hot'.toString()">ci.is_hot DESC, ci.id DESC</when>
                <otherwise>ci.is_recommend DESC, ci.id DESC</otherwise>
            </choose>
            </script>
            """)
    IPage<Car> selectCarPage(Page<Car> page,
                             @Param("type") String type,
                             @Param("keyword") String keyword,
                             @Param("minPrice") BigDecimal minPrice,
                             @Param("maxPrice") BigDecimal maxPrice,
                             @Param("status") String status,
                             @Param("sort") String sort);

    /**
     * 查询推荐车型（首页"热门车型推荐"模块，is_recommend=1）
     */
    @Select("""
            SELECT
                ci.id, ci.name, ci.brand, ci.series,
                CASE ci.type
                    WHEN '超级跑车' THEN 'sports'
                    WHEN '豪华轿车' THEN 'sedan'
                    WHEN '豪华SUV'   THEN 'suv'
                    WHEN '豪华MPV'   THEN 'mpv'
                    WHEN '高端新能源' THEN 'energy'
                    ELSE 'suv'
                END AS type,
                CASE ci.type
                    WHEN '超级跑车' THEN '跑车'
                    WHEN '豪华轿车' THEN '轿车'
                    WHEN '豪华SUV'   THEN 'SUV'
                    WHEN '豪华MPV'   THEN '商务车'
                    WHEN '高端新能源' THEN '新能源'
                    WHEN '定制改装车' THEN '改装车'
                    ELSE ci.type
                END AS type_name,
                ci.daily_price,
                ci.min_rent_days,
                ci.max_rent_days,
                ci.weekly_discount,
                ci.monthly_discount,
                ci.holiday_surcharge,
                CASE ci.status
                    WHEN 'idle'        THEN 'available'
                    WHEN 'returning'   THEN 'available'
                    WHEN 'rented'      THEN 'rented'
                    WHEN 'reserved'    THEN 'maintenance'
                    WHEN 'maintenance' THEN 'maintenance'
                    ELSE 'available'
                END AS status,
                CASE ci.status
                    WHEN 'idle'        THEN '可租'
                    WHEN 'returning'   THEN '可租'
                    WHEN 'rented'      THEN '已出租'
                    WHEN 'reserved'    THEN '维修中'
                    WHEN 'maintenance' THEN '维修中'
                    ELSE '可租'
                END AS status_name,
                ci.images,
                ci.tags, ci.is_hot, ci.is_recommend, ci.seats, ci.displacement, ci.color,
                YEAR(ci.registration_date) AS year, ci.mileage, ci.description,
                5.0 AS rating,
                -- 已租次数：status 为 renting/completed 的订单数
                (SELECT COUNT(*) FROM car_rental.customer_order co
                    WHERE co.car_id = ci.id AND co.is_delete = 0 AND co.status IN ('renting','completed')) AS rent_count,
                -- 累计已租天数：所有 renting/completed 订单 days 之和（无订单时为 0）
                COALESCE((SELECT SUM(co.days) FROM car_rental.customer_order co
                    WHERE co.car_id = ci.id AND co.is_delete = 0 AND co.status IN ('renting','completed')), 0) AS rent_days
            FROM car_rental.car_info ci
            WHERE ci.is_recommend = 1 AND ci.is_delete = 0
            ORDER BY ci.is_hot DESC, ci.id
            """)
    List<Car> selectHotFromAdminDb();

    /**
     * 按 ID 查询单辆车（用于详情页/购物车/订单）
     */
    @Select("""
            SELECT
                ci.id, ci.name, ci.brand, ci.series,
                CASE ci.type
                    WHEN '超级跑车' THEN 'sports'
                    WHEN '豪华轿车' THEN 'sedan'
                    WHEN '豪华SUV'   THEN 'suv'
                    WHEN '豪华MPV'   THEN 'mpv'
                    WHEN '高端新能源' THEN 'energy'
                    ELSE 'suv'
                END AS type,
                CASE ci.type
                    WHEN '超级跑车' THEN '跑车'
                    WHEN '豪华轿车' THEN '轿车'
                    WHEN '豪华SUV'   THEN 'SUV'
                    WHEN '豪华MPV'   THEN '商务车'
                    WHEN '高端新能源' THEN '新能源'
                    WHEN '定制改装车' THEN '改装车'
                    ELSE ci.type
                END AS type_name,
                ci.daily_price,
                ci.min_rent_days,
                ci.max_rent_days,
                ci.weekly_discount,
                ci.monthly_discount,
                ci.holiday_surcharge,
                CASE ci.status
                    WHEN 'idle'        THEN 'available'
                    WHEN 'returning'   THEN 'available'
                    WHEN 'rented'      THEN 'rented'
                    WHEN 'reserved'    THEN 'maintenance'
                    WHEN 'maintenance' THEN 'maintenance'
                    ELSE 'available'
                END AS status,
                CASE ci.status
                    WHEN 'idle'        THEN '可租'
                    WHEN 'returning'   THEN '可租'
                    WHEN 'rented'      THEN '已出租'
                    WHEN 'reserved'    THEN '维修中'
                    WHEN 'maintenance' THEN '维修中'
                    ELSE '可租'
                END AS status_name,
                ci.images,
                ci.tags, ci.is_hot, ci.is_recommend, ci.seats, ci.displacement, ci.color,
                YEAR(ci.registration_date) AS year, ci.mileage, ci.description,
                5.0 AS rating,
                -- 已租次数：status 为 renting/completed 的订单数
                (SELECT COUNT(*) FROM car_rental.customer_order co
                    WHERE co.car_id = ci.id AND co.is_delete = 0 AND co.status IN ('renting','completed')) AS rent_count,
                -- 累计已租天数：所有 renting/completed 订单 days 之和（无订单时为 0）
                COALESCE((SELECT SUM(co.days) FROM car_rental.customer_order co
                    WHERE co.car_id = ci.id AND co.is_delete = 0 AND co.status IN ('renting','completed')), 0) AS rent_days
            FROM car_rental.car_info ci
            WHERE ci.id = #{id} AND ci.is_delete = 0
            """)
    Car selectAdminCarById(@Param("id") Long id);
}
