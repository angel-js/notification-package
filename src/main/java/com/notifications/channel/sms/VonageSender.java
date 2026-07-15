package com.notifications.channel.sms;

import com.notifications.config.sms.MovistarConfig;
import com.notifications.config.sms.VonageConfig;
import com.notifications.core.NotificationSender;
import com.notifications.model.Failure;
import com.notifications.model.NotificationResult;
import com.notifications.model.SmsNotification;
import com.notifications.model.Success;
import com.notifications.utils.MaskSecrets;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
public class VonageSender implements NotificationSender<SmsNotification> {

    private final VonageConfig config;

    public VonageSender(VonageConfig config) {
        this.config = config;
    }

    @Override
    public NotificationResult send(SmsNotification notification) {
        try {
            log.info("[Vonage] POST /v1/sms/send accountSid={}, authToken={}, " +
                            "fromNumber={}, to={}, message={}",
                    MaskSecrets.mask(config.accountSid()),
                    MaskSecrets.mask(config.authToken()),
                    notification.from(), notification.to(),
                    notification.message());

            return Success.of("transactionId_" + UUID.randomUUID());
        } catch (RuntimeException e) {
            return new Failure("VONAGE_ERROR", "Fallo al enviar vía VONAGE SMS", e);
        }
    }

    @Override
    public Class<SmsNotification> supports() {
        return SmsNotification.class;
    }
}
