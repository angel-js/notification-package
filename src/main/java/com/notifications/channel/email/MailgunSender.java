package com.notifications.channel.email;

import com.notifications.config.email.MailGunConfig;
import com.notifications.core.NotificationSender;
import com.notifications.model.EmailNotification;
import com.notifications.model.Failure;
import com.notifications.model.NotificationResult;
import com.notifications.model.Success;
import com.notifications.utils.MaskSecrets;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
public class MailgunSender implements NotificationSender<EmailNotification> {

    private final MailGunConfig config;

    public MailgunSender(MailGunConfig mailGunConfig) {
        this.config = mailGunConfig;
    }

    @Override
    public NotificationResult send(EmailNotification notification) {
        try {
            log.info("[MailgunConfig] POST /v1/email/send apiKey={} domain{} from={} to={} subject='{}' body='{}'",
                    MaskSecrets.mask(config.apiKey()),
                    config.domain(),
                    notification.from(), notification.to(),
                    notification.subject(), notification.body());

            return Success.of("transactionId_" + UUID.randomUUID());
        } catch (RuntimeException e) {
            return new Failure("MAILGUN_ERROR", "Fallo al enviar vía Mailgun", e);
        }
    }

    @Override
    public Class<EmailNotification> supports() {
        return EmailNotification.class;
    }
}
