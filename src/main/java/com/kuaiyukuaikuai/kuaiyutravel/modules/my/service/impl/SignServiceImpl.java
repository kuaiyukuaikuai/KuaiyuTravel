package com.kuaiyukuaikuai.kuaiyutravel.modules.my.service.impl;

import com.kuaiyukuaikuai.kuaiyutravel.modules.my.entity.Sign;
import com.kuaiyukuaikuai.kuaiyutravel.modules.my.mapper.SignMapper;
import com.kuaiyukuaikuai.kuaiyutravel.modules.my.service.SignService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import java.time.LocalDate;

/**
 * 签到记录服务实现类
 */
@Service
public class SignServiceImpl extends ServiceImpl<SignMapper, Sign> implements SignService {

    /**
     * 检查用户是否已经签到
     * @param userId 用户ID
     * @param date 日期
     * @return 是否已经签到
     */
    @Override
    public boolean hasSigned(Long userId, LocalDate date) {
        return query()
                .eq("user_id", userId)
                .eq("date", date)
                .count() > 0;
    }

}