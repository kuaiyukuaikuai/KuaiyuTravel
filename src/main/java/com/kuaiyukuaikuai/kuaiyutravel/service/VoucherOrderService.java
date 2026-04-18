package com.kuaiyukuaikuai.kuaiyutravel.service;

import com.kuaiyukuaikuai.kuaiyutravel.dto.Result;
import com.kuaiyukuaikuai.kuaiyutravel.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author 0
* @description 针对表【tb_voucher_order】的数据库操作Service
* @createDate 2026-04-17 11:08:15
*/
public interface VoucherOrderService extends IService<VoucherOrder> {
    Result seckillVoucher(Long voucherId);

    void createVoucherOrder(VoucherOrder voucherOrder);

/*    Result commonVoucher(Long voucherId);*/
}
