package com.kuaiyukuaikuai.kuaiyutravel.modules.my.service;

import com.kuaiyukuaikuai.kuaiyutravel.modules.my.entity.UserInfo;
import com.kuaiyukuaikuai.kuaiyutravel.modules.my.dto.UserUpdateDTO;
import com.kuaiyukuaikuai.kuaiyutravel.common.utils.Result;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 用户信息服务接口
 */
public interface UserInfoService extends IService<UserInfo> {
    Result getUserInfoDetail(Long userId);
    void updateDetailInfo(Long userId, UserUpdateDTO updateDTO);
}