package com.kuaiyukuaikuai.kuaiyutravel.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kuaiyukuaikuai.kuaiyutravel.dto.Result;
import com.kuaiyukuaikuai.kuaiyutravel.entity.SeckillVoucher;
import com.kuaiyukuaikuai.kuaiyutravel.entity.Voucher;
import com.kuaiyukuaikuai.kuaiyutravel.service.SeckillVoucherService;
import com.kuaiyukuaikuai.kuaiyutravel.service.VoucherService;
import com.kuaiyukuaikuai.kuaiyutravel.mapper.VoucherMapper;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.kuaiyukuaikuai.kuaiyutravel.utils.RedisConstants.*;

/**
* @author 0
* @description 针对表【tb_voucher】的数据库操作Service实现
* @createDate 2026-04-17 11:08:15
*/
@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher>
    implements VoucherService{


    @Resource
    private SeckillVoucherService seckillVoucherService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryVoucherOfPoi(Long poiId) {
        // 查询优惠券信息
        List<Voucher> vouchers = getBaseMapper().queryVoucherOfPoi(poiId);
        // 返回结果
        return Result.ok(vouchers);
    }

    @Override
    @Transactional
    public void addSeckillVoucher(Voucher voucher) {
        // 保存优惠券
        save(voucher);
        // 保存秒杀信息
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(voucher.getStock());
        seckillVoucher.setBeginTime(voucher.getBeginTime());
        seckillVoucher.setEndTime(voucher.getEndTime());
        seckillVoucherService.save(seckillVoucher);
        // 保存秒杀库存到Redis中
        stringRedisTemplate.opsForValue().set(SECKILL_STOCK_KEY + voucher.getId(), voucher.getStock().toString());
    }

}




