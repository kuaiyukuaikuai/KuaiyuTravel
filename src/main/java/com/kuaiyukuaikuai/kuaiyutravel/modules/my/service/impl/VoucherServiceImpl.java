package com.kuaiyukuaikuai.kuaiyutravel.modules.my.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kuaiyukuaikuai.kuaiyutravel.modules.dto.Result;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.entity.SeckillVoucher;
import com.kuaiyukuaikuai.kuaiyutravel.modules.my.entity.Voucher;
import com.kuaiyukuaikuai.kuaiyutravel.modules.my.service.SeckillVoucherService;
import com.kuaiyukuaikuai.kuaiyutravel.modules.my.service.VoucherService;
import com.kuaiyukuaikuai.kuaiyutravel.modules.my.mapper.VoucherMapper;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.kuaiyukuaikuai.kuaiyutravel.utils.RedisConstants.*;

/**
 * 优惠券服务实现类
 */
@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements VoucherService {

    @Resource
    private SeckillVoucherService seckillVoucherService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 查询景点的优惠券
     * @param poiId 景点ID
     * @return 优惠券列表
     */
    @Override
    public Result queryVoucherOfPoi(Long poiId) {
        // 查询优惠券信息
        List<Voucher> vouchers = getBaseMapper().queryVoucherOfPoi(poiId);
        // 返回结果
        return Result.ok(vouchers);
    }

    /**
     * 添加秒杀优惠券
     * @param voucher 优惠券信息
     */
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