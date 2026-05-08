package com.kuaiyukuaikuai.kuaiyutravel.modules.my.dto;

import lombok.Data;

@Data
public class UserPasswordDTO {
    private String oldPassword;
    private String newPassword;
}