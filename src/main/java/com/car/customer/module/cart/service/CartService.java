package com.car.customer.module.cart.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.car.customer.common.exception.BusinessException;
import com.car.customer.common.util.SecurityUtil;
import com.car.customer.entity.Car;
import com.car.customer.entity.Cart;
import com.car.customer.mapper.CartMapper;
import com.car.customer.module.cart.dto.AddCartDTO;
import com.car.customer.module.cart.dto.UpdateCartDTO;
import com.car.customer.module.car.service.CarService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartMapper cartMapper;
    private final CarService carService;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 获取当前会员的购物车列表
     */
    public List<Cart> getCartList() {
        Long memberId = SecurityUtil.getCurrentMemberId();
        LambdaQueryWrapper<Cart> wrapper = new LambdaQueryWrapper<Cart>()
                .eq(Cart::getMemberId, memberId)
                .orderByDesc(Cart::getCreateTime);
        List<Cart> list = cartMapper.selectList(wrapper);
        // 解析 tags JSON 为 tagList
        list.forEach(this::parseTags);
        return list;
    }

    /**
     * 获取购物车数量（用于头部徽标）
     */
    public int getCartCount() {
        Long memberId = SecurityUtil.getCurrentMemberId();
        LambdaQueryWrapper<Cart> wrapper = new LambdaQueryWrapper<Cart>()
                .eq(Cart::getMemberId, memberId);
        return Math.toIntExact(cartMapper.selectCount(wrapper));
    }

    /**
     * 加入购物车（同一车辆已存在则更新租期和数量）
     */
    public Cart addToCart(AddCartDTO dto) {
        Long memberId = SecurityUtil.getCurrentMemberId();

        // 查车辆信息做快照（支持跨库：客户库优先，回退后台库）
        Car car = carService.getCarEntityById(dto.getCarId());
        if (car == null) {
            throw new BusinessException("车辆不存在");
        }
        // 校验最大租期（车辆级 maxRentDays，null 不限；前后端双校验）
        Integer maxDays = carService.resolveMaxRentDays(car);
        if (maxDays != null && dto.getDays() != null && dto.getDays() > maxDays) {
            throw new BusinessException("车辆「" + car.getName() + "」单次最多租 " + maxDays + " 天");
        }
        // 校验最小起租天数（车辆级 minRentDays 与券后价 couponMinDays 取最大值，前后端双校验）
        Integer minDays = carService.resolveMinRentDays(car);
        if (minDays != null && dto.getDays() != null && dto.getDays() < minDays) {
            throw new BusinessException("车辆「" + car.getName() + "」需至少租 " + minDays + " 天起");
        }

        // 检查是否已在购物车
        LambdaQueryWrapper<Cart> wrapper = new LambdaQueryWrapper<Cart>()
                .eq(Cart::getMemberId, memberId)
                .eq(Cart::getCarId, dto.getCarId());
        Cart existing = cartMapper.selectOne(wrapper);

        if (existing != null) {
            // 更新租期和数量
            existing.setStartDate(parseDate(dto.getStartDate()));
            existing.setEndDate(parseDate(dto.getEndDate()));
            existing.setDays(dto.getDays() != null ? dto.getDays() : 1);
            existing.setQuantity(dto.getQuantity() != null ? dto.getQuantity() : 1);
            cartMapper.updateById(existing);
            parseTags(existing);
            return existing;
        }

        // 新增
        Cart cart = new Cart();
        cart.setMemberId(memberId);
        cart.setCarId(car.getId());
        cart.setCarName(car.getName());
        cart.setCarCover(car.getCover());
        cart.setDailyPrice(car.getDailyPrice());
        cart.setTags(car.getTags());
        cart.setStartDate(parseDate(dto.getStartDate()));
        cart.setEndDate(parseDate(dto.getEndDate()));
        cart.setDays(dto.getDays() != null ? dto.getDays() : 1);
        cart.setQuantity(dto.getQuantity() != null ? dto.getQuantity() : 1);
        cartMapper.insert(cart);
        parseTags(cart);
        return cart;
    }

    /**
     * 更新购物车项（租期/数量）
     */
    public Cart updateCart(Long id, UpdateCartDTO dto) {
        Long memberId = SecurityUtil.getCurrentMemberId();
        Cart cart = cartMapper.selectById(id);
        if (cart == null) {
            throw new BusinessException("购物车项不存在");
        }
        if (!memberId.equals(cart.getMemberId())) {
            throw new BusinessException(403, "无权操作");
        }
        // 校验最大租期（车辆级 maxRentDays，null 不限；前后端双校验）
        Car car = carService.getCarEntityById(cart.getCarId());
        Integer maxDays = car != null ? carService.resolveMaxRentDays(car) : null;
        if (maxDays != null && dto.getDays() != null && dto.getDays() > maxDays) {
            throw new BusinessException("车辆「" + (car != null ? car.getName() : "") + "」单次最多租 " + maxDays + " 天");
        }
        if (dto.getStartDate() != null) cart.setStartDate(parseDate(dto.getStartDate()));
        if (dto.getEndDate() != null) cart.setEndDate(parseDate(dto.getEndDate()));
        if (dto.getDays() != null) cart.setDays(dto.getDays());
        if (dto.getQuantity() != null) cart.setQuantity(dto.getQuantity());
        cartMapper.updateById(cart);
        parseTags(cart);
        return cart;
    }

    /**
     * 移除购物车项
     */
    public void removeCart(Long id) {
        Long memberId = SecurityUtil.getCurrentMemberId();
        Cart cart = cartMapper.selectById(id);
        if (cart == null) {
            throw new BusinessException("购物车项不存在");
        }
        if (!memberId.equals(cart.getMemberId())) {
            throw new BusinessException(403, "无权操作");
        }
        cartMapper.deleteById(id);
    }

    /**
     * 清空购物车
     */
    public void clearCart() {
        Long memberId = SecurityUtil.getCurrentMemberId();
        LambdaQueryWrapper<Cart> wrapper = new LambdaQueryWrapper<Cart>()
                .eq(Cart::getMemberId, memberId);
        cartMapper.delete(wrapper);
    }

    // ---------- 工具方法 ----------
    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return null;
        return LocalDate.parse(dateStr, DATE_FMT);
    }

    private void parseTags(Cart cart) {
        if (cart.getTags() == null || cart.getTags().isEmpty()) {
            cart.setTagList(new ArrayList<>());
            return;
        }
        try {
            List<String> tags = objectMapper.readValue(cart.getTags(), new TypeReference<List<String>>() {});
            cart.setTagList(tags);
        } catch (Exception e) {
            cart.setTagList(new ArrayList<>());
        }
    }
}
