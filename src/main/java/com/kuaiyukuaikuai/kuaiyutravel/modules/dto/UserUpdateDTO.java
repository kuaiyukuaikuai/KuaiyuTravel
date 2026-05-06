package com.kuaiyukuaikuai.kuaiyutravel.modules.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class UserUpdateDTO {
    private String nickName;
    private String icon;
    private String city;
    private String introduce;
    private Integer gender;
    private LocalDate birthday;
}