package com.kuaiyukuaikuai.kuaiyutravel.mapper;

import com.kuaiyukuaikuai.kuaiyutravel.entity.Voucher;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
* @author 0
* @description 针对表【tb_voucher】的数据库操作Mapper
* @createDate 2026-04-17 11:08:15
* @Entity com.kuaiyukuaikuai.kuaiyutravel.entity.Voucher
*/
public interface VoucherMapper extends BaseMapper<Voucher> {

    List<Voucher> queryVoucherOfPoi(@Param("poiId") Long poiId);
}




