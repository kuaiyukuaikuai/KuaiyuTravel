package com.kuaiyukuaikuai.kuaiyutravel.service.impl;

import com.kuaiyukuaikuai.kuaiyutravel.entity.UserInfo;
import com.kuaiyukuaikuai.kuaiyutravel.mapper.UserInfoMapper;
import com.kuaiyukuaikuai.kuaiyutravel.service.UserInfoService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 用户信息服务实现类
 */
@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements UserInfoService {

}