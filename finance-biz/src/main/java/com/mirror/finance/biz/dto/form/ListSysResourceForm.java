package com.mirror.finance.biz.dto.form;


import lombok.Data;

import java.util.List;

/**
 * 获取资源
 */
@Data
public class ListSysResourceForm {
 //   @ApiModelProperty(value = "资源名称")
    private String name;

  //  @ApiModelProperty(value = "资源路径")
    private String path;
}
