package com.mirror.finance.biz.dto.form;


import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Date;

/**
 * 辅助核算（部门）
 *
 */
@Data
public class CreateAssistCalculateDepartmentForm extends CreateAssistCalculateForm {
   // @ApiModelProperty(value = "负责人")
    private String manager;

   // @ApiModelProperty(value = "手机")
    @Pattern(regexp = "^(\\d{11})?$", message = "手机格式不正确")
    private String phone;

   // @ApiModelProperty(value = "成立日期")
    private Date startDate;

  //  @ApiModelProperty(value = "撤销日期")
    private Date revokeDate;
}