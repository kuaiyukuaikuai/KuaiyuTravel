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
 * 创建旅行团、根据团号查询旅行团信息、更新旅行团信息、删除旅行团以及分页查询旅行团列表等功能。
 * </p>
 *
 * @author KuaiyuTravel
 * @version 1.0.0
 * @since 1.0.0
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
     * @param group 组团实体对象，包含旅行团的基本信息（如团名、出发地、目的地、出发时间等）
     * @return Result 统一响应结果，成功时返回创建结果提示或数据，失败时返回错误状态码及信息
     */
    @PostMapping("/create")
    public Result createGroup(@RequestBody Group group) {
        return groupService.createGroup(group);
    }

    /**
     * 根据团号查询组团信息
     * <p>
     * 通过唯一的旅行团编号（GroupNo）获取该旅行团的详细信息。
     * </p>
     *
     * @param groupNo 组团编号，路径参数，唯一标识一个旅行团
     * @return Result 统一响应结果，成功时 data 包含对应的组团详情，失败时返回相应的错误提示
     */
    @GetMapping("/query/{groupNo}")
    public Result queryByGroupNo(@PathVariable("groupNo") String groupNo) {
        return groupService.queryByGroupNo(groupNo);
    }

    /**
     * 更新组团信息
     * <p>
     * 接收包含主键及需要修改字段的旅行团实体对象，更新对应的旅行团信息。
     * </p>
     *
     * @param group 组团实体对象，必须包含有效的主键 ID 以及待更新的字段信息
     * @return Result 统一响应结果，成功时返回更新成功提示，失败时返回错误状态码及信息
     */
    @PutMapping("/update")
    public Result updateGroup(@RequestBody Group group) {
        return groupService.updateGroup(group);
    }

    /**
     * 根据 ID 删除组团信息
     * <p>
     * 根据传入的旅行团主键 ID，在系统中进行物理或逻辑删除。
     * </p>
     *
     * @param id 组团实体主键 ID，路径参数
     * @return Result 统一响应结果，成功时返回删除成功提示，失败时返回相应的错误信息
     */
    @DeleteMapping("/{id}")
    public Result deleteGroup(@PathVariable("id") Long id) {
        return groupService.deleteGroup(id);
    }

    /**
     * 分页查询组团列表
     * <p>
     * 获取系统中的旅行团列表数据，支持分页显示。默认查询第一页，每页10条记录。
     * </p>
     *
     * @param current 当前页码，默认值为 1
     * @param size    每页显示的记录数，默认值为 10
     * @return Result 统一响应结果，成功时 data 中包含分页对象（包含当前页数据、总记录数等）
     */
    @GetMapping("/list")
    public Result queryGroupPage(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "size", defaultValue = "10") Integer size) {
        return groupService.queryGroupPage(current, size);
    }
}
