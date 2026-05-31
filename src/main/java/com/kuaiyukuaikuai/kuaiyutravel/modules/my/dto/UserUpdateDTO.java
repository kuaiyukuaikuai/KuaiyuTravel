package com.kuaiyukuaikuai.kuaiyutravel.modules.my.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UserUpdateDTO {

    @Size(max = 20, message = "昵称长度不能超过20个字符")
    private String nickName;

    @Size(max = 500, message = "头像链接长度不能超过500个字符")
    private String icon;

    @Size(max = 50, message = "城市名称长度不能超过50个字符")
    private String city;

    @Size(max = 500, message = "个人简介长度不能超过500个字符")
    private String introduce;

    @Min(value = 0, message = "性别参数不合法")
    @Max(value = 2, message = "性别参数不合法")
    private Integer gender;

    private LocalDate birthday;
}
