package com.kuaiyukuaikuai.kuaiyutravel.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kuaiyukuaikuai.kuaiyutravel.entity.Sign;
import com.kuaiyukuaikuai.kuaiyutravel.service.SignService;
import com.kuaiyukuaikuai.kuaiyutravel.mapper.SignMapper;
import org.springframework.stereotype.Service;

/**
 * 签到服务实现类
 */
@Service
public class SignServiceImpl extends ServiceImpl<SignMapper, Sign> implements SignService {

}