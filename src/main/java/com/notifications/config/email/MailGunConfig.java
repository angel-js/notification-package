package com.notifications.config.email;

import com.notifications.utils.MaskSecrets;
import lombok.Builder;

import java.util.Objects;

@Builder
public record MailGunConfig(String apiKey, String domain) {
    public MailGunConfig {
        Objects.requireNonNull(apiKey, "apiKey");
        Objects.requireNonNull(domain, "domain");
    }
    @Override
    public String toString() {
        return "MailgunConfig{apiKey=" + MaskSecrets.mask(apiKey)
                + ", domain=" + domain + "}";
    }
}
