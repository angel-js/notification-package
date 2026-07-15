package com.notifications.channel.push;

import com.notifications.config.push.AndroidConfig;
import com.notifications.core.NotificationSender;
import com.notifications.model.Failure;
import com.notifications.model.NotificationResult;
import com.notifications.model.PushNotification;
import com.notifications.model.Success;
import com.notifications.utils.MaskSecrets;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
public class AndroidSender implements NotificationSender<PushNotification> {

    private final AndroidConfig config;

    public AndroidSender(AndroidConfig config) {
        this.config = config;
    }

    @Override
    public NotificationResult send(PushNotification notification) {
        try {
            log.info("[Android PUSH] POST /v1/push/send apiKey={} serverKey={} deviceToken={} title='{}' body='{}'",
                    MaskSecrets.mask(config.apiKey()),
                    MaskSecrets.mask(config.serverKey()),
                    notification.deviceToken(),
                    notification.title(), notification.body());

            return Success.of("transactionId_" + UUID.randomUUID());
        } catch (RuntimeException e) {
            return new Failure("ANDROID_ERROR", "Fallo al enviar vía Android Push", e);
        }
    }

    @Override
    public Class<PushNotification> supports() {
        return PushNotification.class;
    }
}
