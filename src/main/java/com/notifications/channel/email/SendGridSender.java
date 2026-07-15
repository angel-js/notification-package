package com.notifications.channel.email;


import com.notifications.config.email.SendGridConfig;
import com.notifications.core.NotificationSender;
import com.notifications.model.EmailNotification;
import com.notifications.model.Failure;
import com.notifications.model.NotificationResult;
import com.notifications.model.Success;
import com.notifications.utils.MaskSecrets;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.UUID;

@Slf4j
public class SendGridSender implements NotificationSender<EmailNotification> {

    private final SendGridConfig config;

    public SendGridSender(SendGridConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    @Override
    public NotificationResult send(EmailNotification notification) {
        try {
            log.info("[SendGrid] POST /v1/email/send apiKey={} from={} to={} subject='{}' body='{}'",
                    MaskSecrets.mask(config.apiKey()),
                    notification.from(), notification.to(),
                    notification.subject(), notification.body());

            return Success.of("transactionId_" + UUID.randomUUID());
        } catch (RuntimeException e) {
            return new Failure("SENDGRID_ERROR", "Fallo al enviar vía SendGrid", e);
        }
    }

    @Override
    public Class<EmailNotification> supports() {
        return EmailNotification.class;
    }
}
