package com.kuaiyukuaikuai.kuaiyutravel.controller;

import com.kuaiyukuaikuai.kuaiyutravel.dto.Result;
import com.kuaiyukuaikuai.kuaiyutravel.service.VoucherOrderService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;

/**
 * 优惠券订单控制器
 * 处理优惠券订单相关的请求
 * 
 * @author 虎哥
 * @since 2021-12-22
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
        return voucherOrderService.seckillVoucher(voucherId);
    }

    /**
     * 普通优惠券
     * 
     * @param voucherId 优惠券id
     * @return 订单信息
     */
    @PostMapping("common/{id}")
    public Result commonVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.commonVoucher(voucherId);
    }
}