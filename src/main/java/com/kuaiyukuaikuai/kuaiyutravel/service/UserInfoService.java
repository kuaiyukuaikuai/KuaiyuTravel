package com.kuaiyukuaikuai.kuaiyutravel.service;

import com.kuaiyukuaikuai.kuaiyutravel.entity.UserInfo;
import com.kuaiyukuaikuai.kuaiyutravel.dto.UserUpdateDTO;
import com.kuaiyukuaikuai.kuaiyutravel.dto.Result;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 用户信息服务接口
 */
public interface UserInfoService extends IService<UserInfo> {
    Result getUserInfoDetail(Long userId);
    void updateDetailInfo(Long userId, UserUpdateDTO updateDTO);
}