package com.kuaiyukuaikuai.kuaiyutravel.modules.departure.controller;

import com.kuaiyukuaikuai.kuaiyutravel.common.utils.Result;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.service.VoucherOrderService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

/**
 * 优惠券订单控制器
 * 处理优惠券订单相关的请求
 *
 * @author 0
 * @since 2026-02-17
 */
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {
    @Resource
    private VoucherOrderService voucherOrderService;

    /**
     * 秒杀优惠券
     *
     * @param voucherId 优惠券id
     * @return 订单信息
     */
    @PostMapping("seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        return Result.ok(voucherOrderService.seckillVoucher(voucherId));
    }

    /**
     * 普通优惠券
     *
     * @param voucherId 优惠券id
     * @return 订单信息
     */
    @PostMapping("common/{id}")
    public Result commonVoucher(@PathVariable("id") Long voucherId) {
        return Result.ok(voucherOrderService.commonVoucher(voucherId));
    }

    /**
     * 查询我的订单列表
     *
     * @return 订单列表
     */
    @GetMapping("/my-orders")
    public Result queryMyOrders() {
        return Result.ok(voucherOrderService.queryMyOrders());
    }
}
