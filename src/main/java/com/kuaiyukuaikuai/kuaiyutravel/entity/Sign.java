package com.kuaiyukuaikuai.kuaiyutravel.entity;

import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

import java.util.Date;
import io.swagger.v3.oas.annotations.media.Schema;

/**
* 
* @TableName tb_sign
*/
public class Sign implements Serializable {

    /**
    * 主键
    */
    @NotNull(message="[主键]不能为空")
    @Schema(description = "主键")
    private Long id;
    /**
    * 用户id
    */
    @NotNull(message="[用户id]不能为空")
    @Schema(description = "用户id")
    private Long userId;
    /**
    * 签到的年
    */
    @NotNull(message="[签到的年]不能为空")
    @Schema(description = "签到的年")
    private Object year;
    /**
    * 签到的月
    */
    @NotNull(message="[签到的月]不能为空")
    @Schema(description = "签到的月")
    private Integer month;
    /**
    * 签到的日期
    */
    @NotNull(message="[签到的日期]不能为空")
    @Schema(description = "签到的日期")
    private Date date;
    /**
    * 是否补签
    */
    @Schema(description = "是否补签")
    private Integer isBackup;

    /**
    * 主键
    */
    private void setId(Long id){
    this.id = id;
    }

    /**
    * 用户id
    */
    private void setUserId(Long userId){
    this.userId = userId;
    }

    /**
    * 签到的年
    */
    private void setYear(Object year){
    this.year = year;
    }

    /**
    * 签到的月
    */
    private void setMonth(Integer month){
    this.month = month;
    }

    /**
    * 签到的日期
    */
    private void setDate(Date date){
    this.date = date;
    }

    /**
    * 是否补签
    */
    private void setIsBackup(Integer isBackup){
    this.isBackup = isBackup;
    }


    /**
    * 主键
    */
    private Long getId(){
    return this.id;
    }

    /**
    * 用户id
    */
    private Long getUserId(){
    return this.userId;
    }

    /**
    * 签到的年
    */
    private Object getYear(){
    return this.year;
    }

    /**
    * 签到的月
    */
    private Integer getMonth(){
    return this.month;
    }

    /**
    * 签到的日期
    */
    private Date getDate(){
    return this.date;
    }

    /**
    * 是否补签
    */
    private Integer getIsBackup(){
    return this.isBackup;
    }

}
