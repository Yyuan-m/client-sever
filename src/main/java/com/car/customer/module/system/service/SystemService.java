package com.car.customer.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.car.customer.entity.Advantage;
import com.car.customer.entity.Review;
import com.car.customer.entity.Store;
import com.car.customer.entity.SysDictData;
import com.car.customer.entity.SystemConfig;
import com.car.customer.mapper.AdvantageMapper;
import com.car.customer.mapper.ReviewMapper;
import com.car.customer.mapper.StoreMapper;
import com.car.customer.mapper.SysDictDataMapper;
import com.car.customer.mapper.SystemConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统服务（配置/门店/优势/评价/字典均来自 car_rental 库）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemService {

    private final SystemConfigMapper systemConfigMapper;
    private final StoreMapper storeMapper;
    private final AdvantageMapper advantageMapper;
    private final ReviewMapper reviewMapper;
    private final SysDictDataMapper sysDictDataMapper;

    /**
     * 网站配置：将 car_rental.sys_config 的 key-value 组装为前端期望的 camelCase 对象
     * car_rental.sys_config 的 key 与原客户库不同，需做映射兼容
     */
    public Map<String, Object> getSystemConfig() {
        List<SystemConfig> configs = systemConfigMapper.selectList(null);
        Map<String, Object> result = new HashMap<>();
        // rentRules 子对象
        Map<String, Object> rentRules = new HashMap<>();

        for (SystemConfig config : configs) {
            String key = config.getConfigKey();
            String value = config.getConfigValue();
            if (value == null) continue;
            switch (key) {
                case "site_name" -> result.put("siteName", value);
                case "contact_phone", "hotline" -> {
                    // 优先 contact_phone，兜底 hotline
                    result.putIfAbsent("phone", value);
                }
                case "contact_email" -> result.put("email", value);
                case "address" -> result.put("address", value);
                case "min_rent_days" -> rentRules.put("minDays", parseInt(value, 1));
                case "max_rent_days" -> rentRules.put("maxDays", parseInt(value, 30));
                case "late_fee_per_day" -> rentRules.put("overtimeFee", parseInt(value, 200));
                case "cancellation_policy" -> rentRules.put("cancelPolicy", value);
                default -> result.put(key, value);
            }
        }

        // rentRules 默认值兜底
        rentRules.putIfAbsent("minDays", 1);
        rentRules.putIfAbsent("maxDays", 30);
        rentRules.putIfAbsent("overtimeFee", 200);
        rentRules.putIfAbsent("cancelPolicy", "提前24小时取消免费");
        result.put("rentRules", rentRules);

        // 前端 footer 用到 siteSubtitle，sys_config 无此 key，用默认值
        result.putIfAbsent("siteSubtitle", "豪华汽车租赁");
        result.putIfAbsent("siteName", "LUXURY CAR");
        result.putIfAbsent("phone", "400-888-8888");

        return result;
    }

    public List<Store> getStores() {
        return storeMapper.selectList(new LambdaQueryWrapper<Store>()
                .orderByAsc(Store::getId));
    }

    public List<Advantage> getAdvantages() {
        return advantageMapper.selectList(new LambdaQueryWrapper<Advantage>()
                .orderByAsc(Advantage::getSortOrder));
    }

    public List<Review> getReviews() {
        return reviewMapper.selectList(new LambdaQueryWrapper<Review>()
                .orderByDesc(Review::getDate));
    }

    /**
     * 按类型查询字典数据（跨库 car_rental.sys_dict_data）
     * @param dictType 字典类型，如 vehicle_type / vehicle_brand 等
     * @return 启用状态的字典数据列表，按 sort_order 升序
     */
    public List<SysDictData> getDictByType(String dictType) {
        return sysDictDataMapper.selectList(new LambdaQueryWrapper<SysDictData>()
                .eq(SysDictData::getDictType, dictType)
                .eq(SysDictData::getStatus, 1)
                .orderByAsc(SysDictData::getSortOrder));
    }

    private int parseInt(String value, int defaultValue) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
