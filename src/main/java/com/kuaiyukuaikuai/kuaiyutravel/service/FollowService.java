package com.kuaiyukuaikuai.kuaiyutravel.service;

import com.kuaiyukuaikuai.kuaiyutravel.dto.Result;
import com.kuaiyukuaikuai.kuaiyutravel.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 关注服务接口
 */
public interface FollowService extends IService<Follow> {

    /**
     * 关注或取消关注
     * @param followUserId 被关注用户ID
     * @param isFollow 是否关注
     * @return 操作结果
     */
    Result follow(Long followUserId, Boolean isFollow);

    /**
     * 查询是否关注
     * @param followUserId 被关注用户ID
     * @return 关注状态
     */
    Result isFollow(Long followUserId);

    /**
     * 获取共同关注
     * @param id 用户ID
     * @return 共同关注用户列表
     */
    Result followCommons(Long id);

    /**
     * 查询粉丝列表
     * @param id 用户ID
     * @param current 当前页码
     * @return 粉丝列表
     */
    Result queryFans(Long id, Integer current);

    /**
     * 查询关注列表
     * @param id 用户ID
     * @param current 当前页码
     * @return 关注列表
     */
    Result queryFollowings(Long id, Integer current);
}