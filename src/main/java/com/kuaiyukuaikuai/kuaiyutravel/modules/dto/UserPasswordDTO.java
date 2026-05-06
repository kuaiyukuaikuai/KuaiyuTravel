package com.kuaiyukuaikuai.kuaiyutravel.modules.dto;

import lombok.Data;

@Data
public class UserPasswordDTO {
    private String oldPassword;
    private String newPassword;
}