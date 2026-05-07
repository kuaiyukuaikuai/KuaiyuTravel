package com.kuaiyukuaikuai.kuaiyutravel.modules.departure.service;

import com.kuaiyukuaikuai.kuaiyutravel.modules.dto.Result;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.entity.Voucher;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 优惠券服务接口
 */
public interface VoucherService extends IService<Voucher> {
    /**
     * 查询景点的优惠券
     * @param poiId 景点ID
     * @return 优惠券列表
     */
    Result queryVoucherOfPoi(Long poiId);

    /**
     * 添加秒杀优惠券
     * @param voucher 优惠券信息
     */
    void addSeckillVoucher(Voucher voucher);
}