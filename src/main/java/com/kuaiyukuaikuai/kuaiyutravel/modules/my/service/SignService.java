package com.kuaiyukuaikuai.kuaiyutravel.modules.my.service;

import com.kuaiyukuaikuai.kuaiyutravel.modules.my.entity.Sign;
import com.baomidou.mybatisplus.extension.service.IService;
import java.time.LocalDate;

/**
 * 签到记录服务接口
 */
public interface SignService extends IService<Sign> {

    /**
     * 检查用户是否已经签到
     * @param userId 用户ID
     * @param date 日期
     * @return 是否已经签到
     */
    boolean hasSigned(Long userId, LocalDate date);

}