package com.kuaiyukuaikuai.kuaiyutravel.modules.departure.service;

import com.kuaiyukuaikuai.kuaiyutravel.common.utils.Result;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 优惠券订单服务接口
 */
public interface VoucherOrderService extends IService<VoucherOrder> {
    /**
     * 秒杀优惠券
     * @param voucherId 优惠券ID
     * @return 操作结果
     */
    Result seckillVoucher(Long voucherId);

    /**
     * 创建优惠券订单
     * @param voucherOrder 订单信息
     */
    void createVoucherOrder(VoucherOrder voucherOrder);

    /**
     * 普通优惠券
     * @param voucherId 优惠券ID
     * @return 操作结果
     */
    Result commonVoucher(Long voucherId);
}