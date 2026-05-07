package com.kuaiyukuaikuai.kuaiyutravel.modules.my.service.impl;

import com.kuaiyukuaikuai.kuaiyutravel.modules.my.entity.UserInfo;
import com.kuaiyukuaikuai.kuaiyutravel.modules.my.mapper.UserInfoMapper;
import com.kuaiyukuaikuai.kuaiyutravel.modules.my.service.UserInfoService;
import com.kuaiyukuaikuai.kuaiyutravel.modules.dto.UserUpdateDTO;
import com.kuaiyukuaikuai.kuaiyutravel.common.utils.Result;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 用户信息服务实现类
 */
@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements UserInfoService {
    @Override
    public Result getUserInfoDetail(Long userId) {
        // 查询详情
        UserInfo info = getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.ok(info);
    }

    @Override
    public void updateDetailInfo(Long userId, UserUpdateDTO updateDTO) {
        // 构建 UserInfo 对象
        UserInfo userInfo = new UserInfo();
        userInfo.setUserId(userId);
        userInfo.setCity(updateDTO.getCity());
        userInfo.setIntroduce(updateDTO.getIntroduce());
        userInfo.setGender(updateDTO.getGender() != null ? updateDTO.getGender() == 1 : null);
        userInfo.setBirthday(updateDTO.getBirthday());

        // 调用 saveOrUpdate 方法，有则更新，无则插入
        saveOrUpdate(userInfo);
    }
}