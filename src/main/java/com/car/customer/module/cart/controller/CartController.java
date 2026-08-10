package com.car.customer.module.cart.controller;

import com.car.customer.common.result.Result;
import com.car.customer.entity.Cart;
import com.car.customer.module.cart.dto.AddCartDTO;
import com.car.customer.module.cart.dto.UpdateCartDTO;
import com.car.customer.module.cart.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    /** 购物车列表 */
    @GetMapping("/list")
    public Result<List<Cart>> list() {
        return Result.ok(cartService.getCartList());
    }

    /** 购物车数量（头部徽标） */
    @GetMapping("/count")
    public Result<Map<String, Integer>> count() {
        return Result.ok(Map.of("count", cartService.getCartCount()));
    }

    /** 加入购物车 */
    @PostMapping("/add")
    public Result<Cart> add(@Valid @RequestBody AddCartDTO dto) {
        return Result.ok(cartService.addToCart(dto));
    }

    /** 更新购物车项 */
    @PutMapping("/update/{id}")
    public Result<Cart> update(@PathVariable Long id, @RequestBody UpdateCartDTO dto) {
        return Result.ok(cartService.updateCart(id, dto));
    }

    /** 移除购物车项 */
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        cartService.removeCart(id);
        return Result.ok();
    }

    /** 清空购物车 */
    @DeleteMapping("/clear")
    public Result<Void> clear() {
        cartService.clearCart();
        return Result.ok();
    }
}
