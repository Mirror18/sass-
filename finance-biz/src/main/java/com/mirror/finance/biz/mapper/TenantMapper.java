package com.mirror.finance.biz.mapper;

import com.mirror.finance.biz.domain.Tenant;
import com.mirror.mybatis.help.CommonMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TenantMapper extends CommonMapper<Tenant> {
}