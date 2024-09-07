package com.mirror.finance.biz.dto.form;


import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
public class CreateSysRoleForm {

    /**
     * 角色名称
     */
 //   @ApiModelProperty(value = "角色名称")
    @NotBlank
    private String roleName;

    /**
     * 是否禁用
     */
  //  @ApiModelProperty(value = "是否禁用")
    @NotNull
    private Boolean disable;
}