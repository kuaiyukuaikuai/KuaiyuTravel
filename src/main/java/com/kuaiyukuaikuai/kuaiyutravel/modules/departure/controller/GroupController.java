package com.kuaiyukuaikuai.kuaiyutravel.modules.departure.controller;

import com.kuaiyukuaikuai.kuaiyutravel.common.utils.Result;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.entity.Group;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.service.GroupService;
import org.springframework.web.bind.annotation.*;
import jakarta.annotation.Resource;

/**
 * 组团出发控制器
 * <p>
 * 提供组团业务相关的 RESTful API 接口，包括：
 * 创建旅行团、根据团号查询/更新/删除旅行团信息、分页查询旅行团列表等功能。
 * 所有接口统一使用 groupNo（String类型）作为唯一标识，避免前后端 Long 精度丢失问题。
 * </p>
 *
 * @author KuaiyuTravel
 * @version 2.0.0
 * @since 2.0.0
 */
@RestController
@RequestMapping("/group")
public class GroupController {

    @Resource
    private GroupService groupService;

    /**
     * 创建组团信息
     * <p>
     * 接收前端传递的旅行团实体数据并进行持久化保存，生成新的旅行团记录。
     * </p>
     *
     * @param group 组团实体对象
     * @return Result 返回创建的组团编号（groupNo）
     */
    @PostMapping("/create")
    public Result createGroup(@RequestBody Group group) {
        return groupService.createGroup(group);
    }

    /**
     * 根据团号查询组团信息
     *
     * @param groupNo 组团编号（String 类型）
     * @return Result 组团详情
     */
    @GetMapping("/query/{groupNo}")
    public Result queryByGroupNo(@PathVariable("groupNo") String groupNo) {
        return groupService.queryByGroupNo(groupNo);
    }

    /**
     * 更新组团信息（团长权限）
     * <p>
     * 接收包含 groupNo 及需要修改字段的实体对象，更新对应的组团信息。
     * </p>
     *
     * @param group 组团实体对象（必须包含 groupNo）
     * @return Result 更新结果
     */
    @PutMapping("/update")
    public Result updateGroup(@RequestBody Group group) {
        return groupService.updateGroup(group);
    }

    /**
     * 根据团号解散/删除组团（团长权限）
     * <p>
     * 通过唯一的旅行团编号进行删除操作。
     * </p>
     *
     * @param groupNo 组团编号（String 类型）
     * @return Result 删除结果
     */
    @DeleteMapping("/{groupNo}")
    public Result deleteGroup(@PathVariable("groupNo") String groupNo) {
        return groupService.deleteGroupByNo(groupNo);
    }

    /**
     * 分页查询组团列表（招募中）
     *
     * @param current 当前页码
     * @param size    每页大小
     * @return Result 分页数据
     */
    @GetMapping("/list")
    public Result queryGroupPage(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "size", defaultValue = "10") Integer size) {
        return groupService.queryGroupPage(current, size);
    }
}
