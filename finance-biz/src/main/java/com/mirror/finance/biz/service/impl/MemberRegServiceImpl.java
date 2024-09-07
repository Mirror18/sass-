package com.mirror.finance.biz.service.impl;

import com.mirror.common.dto.TokenResponse;
import com.mirror.common.exception.BizException;
import com.mirror.common.exception.ParameterException;
import com.mirror.common.service.TokenService;
import com.mirror.finance.biz.config.ObjectConvertor;
import com.mirror.finance.biz.constant.RedisKeyConstant;
import com.mirror.finance.biz.domain.MemberBindPhone;
import com.mirror.finance.biz.domain.MemberBindWxOpenId;
import com.mirror.finance.biz.dto.AdminDTO;
import com.mirror.finance.biz.dto.form.PhoneRegisterForm;
import com.mirror.finance.biz.dto.vo.GenerateMpRegCodeVo;
import com.mirror.finance.biz.enums.SmsCodeTypeEnum;
import com.mirror.finance.biz.service.*;
import com.mirror.wx.config.WxConfig;
import com.mirror.wx.dto.AccessTokenResult;
import com.mirror.wx.dto.MpQrCodeCreateRequest;
import com.mirror.wx.dto.MpQrCodeCreateResult;
import com.mirror.wx.dto.MpSubscribeEventRequest;
import com.mirror.wx.service.WXService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.concurrent.TimeUnit;


@Service
@Slf4j
@RequiredArgsConstructor
public class MemberRegServiceImpl implements MemberRegService {
    final MemberLoginService memberLoginService;
    final RedissonClient redissonClient;
    final MemberBindPhoneService memberBindPhoneService;
    final TransactionTemplate transactionTemplate;
    final TenantService tenantService;
    final MemberService memberService;
    final WxConfig wxConfig;
    final WXService wxService;
    final ObjectConvertor objectConvertor;
    final RedisTemplate<String, Object> redisTemplate;
    final TokenService<AdminDTO> adminTokenService;
    final MemberBindWxOpenIdService memberBindWxOpenIdService;

    /**
     * 注册 保存到数据库
     *
     * @param request
     * @return
     */
    @Override
    public long phoneReg(PhoneRegisterForm request) {
        if (!Objects.equals(request.getPassword(), request.getConfirmPassword())) {
            throw new ParameterException("两次输入的密码不一致");
        }
        memberLoginService.checkSmsCode(request.getPhone(), request.getSmsCode(), SmsCodeTypeEnum.REG.getCode());

        RLock rLock = redissonClient.getLock(RedisKeyConstant.PHONE_CHANGE + request.getPhone());
        try {
            rLock.lock();
            MemberBindPhone memberBindPhone = memberBindPhoneService.getMemberByPhone(request.getPhone());
            if (memberBindPhone != null) {
                log.warn("手机号：{}已注册", request.getPhone());
                throw new BizException("手机号已注册");
            }
            //将游客数据入口（保证数据一致性）
            Long memberId = transactionTemplate.execute(transactionStatus -> {
                long tenantId = tenantService.add();
                long id = memberService.reg(tenantId);
                if (id <= 0) {
                    throw new BizException("注册异常");
                }
                memberBindPhoneService.reg(request.getPhone(), id, request.getPassword());
                return id;
            });
            if (memberId == null) {
                throw new BizException("注册失败");
            }
            return memberId;
        } catch (Exception ex) {
            throw new BizException("注册失败", ex);
        } finally {
            rLock.unlock();
        }
    }

    /**
     * 生成微信公众号二维码 用于关注注册
     *
     * @param clientId
     * @return
     */
    @Override
    public GenerateMpRegCodeVo generateMpRegCode(String clientId) {
        AccessTokenResult accessTokenResult = wxService.getMpAccessTokenByCache(wxConfig.getMp().getAppId());
        MpQrCodeCreateRequest request = new MpQrCodeCreateRequest();
        request.setExpireSeconds(wxConfig.getMp().getCodeExpire());
        request.setActionName("QR_STR_SCENE");
        request.setActionInfo(request.new ActionInfo());
        request.getActionInfo().setScene(request.new scene());
        request.getActionInfo().getScene().setSceneStr("ScanReg_" + wxConfig.getMp().getAppId() + "_" + clientId);
        MpQrCodeCreateResult result = wxService.createMpQrcodeCreate(accessTokenResult.getAccessToken(), request);

        return objectConvertor.toGenerateMpRegCodeResponse(result);
    }

    @EventListener
    @Override
    public void handleMpSubscribeEventRequest(MpSubscribeEventRequest mpSubscribeEventRequest) {
        log.info("接收到消息：MpSubscribeEventRequest：{}", mpSubscribeEventRequest.toString());
        log.info("0:{}", mpSubscribeEventRequest.getEvent());
        if ("subscribe".equals(mpSubscribeEventRequest.getEvent())
                && Strings.isNotBlank(mpSubscribeEventRequest.getEventKey())) {
            String[] keys = mpSubscribeEventRequest.getEventKey().split("_");
            if ("qrscene".equals(keys[0]) && "ScanReg".equals(keys[1])) {
                log.info("AppId：{}，ClientId：{}", keys[2], keys[3]);
                registerByMpOpenId(keys[2], keys[3], mpSubscribeEventRequest.getToUserName());
                return;
            }
        }

        if ("SCAN".equals(mpSubscribeEventRequest.getEvent()) &&
                Strings.isNotBlank(mpSubscribeEventRequest.getEventKey())) {
            String[] keys = mpSubscribeEventRequest.getEventKey().split("_");
            if ("ScanReg".equals(keys[0])) {
                log.info("AppId：{}，ClientId：{}", keys[1], keys[2]);
                registerByMpOpenId(keys[1], keys[2], mpSubscribeEventRequest.getToUserName());
                return;
            }
        }
    }

    @Override
    public TokenResponse registerByMpOpenId(String appId, String clientId, String openId) {
        long memberId = scReg(appId, openId);
        AdminDTO adminDTO = new AdminDTO();
        adminDTO.setId(memberId);
        adminTokenService.setToken(adminDTO);
        redisTemplate.opsForValue().set(RedisKeyConstant.CLIENT_TOKEN_KEY + clientId, adminDTO.getToken(), 10, TimeUnit.MINUTES);
        return adminDTO.getToken();
    }

    /**
     * 扫描注册
     *
     * @param appId
     * @param openId
     * @return
     */
    @Override
    public long scReg(String appId, String openId) {
        MemberBindWxOpenId memberBindWxOpenId = memberBindWxOpenIdService.get(appId, openId);
        if (Objects.nonNull(memberBindWxOpenId)) {
            return memberBindWxOpenId.getMemberId();
        }

        //将游客数据入口（保证数据一致性）
        Long memberId = transactionTemplate.execute(transactionStatus -> {
            //创建租户id
            long tenantId = tenantService.add();
            long id = memberService.reg(tenantId);
            memberBindWxOpenIdService.reg(appId, openId, id);
            return id;
        });
        if (memberId == null) {
            throw new BizException("注册失败");
        }
        return memberId;
    }
}
