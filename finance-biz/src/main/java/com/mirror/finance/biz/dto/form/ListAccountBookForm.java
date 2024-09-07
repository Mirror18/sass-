package com.mirror.finance.biz.dto.form;

import com.mirror.common.dto.PageHelperRequest;

import lombok.Data;

@Data
public class ListAccountBookForm extends PageHelperRequest {
    /**
     * 公司名称
     */
  //  @ApiModelProperty(value = "公司名称")
    private String companyName;

    /**
     * 是否禁用；true：禁用；false：启用
     */
//    @ApiModelProperty(value = "是否禁用；true：禁用；false：启用")
    private Boolean disable;
}
