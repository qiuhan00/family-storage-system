package com.family.storage.notify.channel;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 短信通道（spec §6 扩展点）：配置 sms.enabled=true 时注册为 Bean，
 * 使 NotificationChannel 同时存在两个实现；当前实现为空操作（no-op），
 * 接入厂商后在此调用短信网关，凭据一律经环境变量注入。
 * sms.enabled=false（默认）时按 @ConditionalOnProperty 不注册，实例不发送。
 */
@Component
@ConditionalOnProperty(prefix = "sms", name = "enabled", havingValue = "true")
public class SmsChannel implements NotificationChannel {

    @Override
    public ChannelType type() {
        return ChannelType.SMS;
    }

    @Override
    public void send(NotificationContext ctx) {
        // 扩展点：预留短信投递；非真实发送。
    }
}