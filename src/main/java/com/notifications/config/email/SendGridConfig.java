package com.notifications.config.email;

import com.notifications.utils.MaskSecrets;

import java.util.Objects;

public record SendGridConfig(String apiKey) {
    public SendGridConfig {
        Objects.requireNonNull(apiKey, "apiKey");
    }
    @Override
    public String toString() {
        return "SendGridConfig{apiKey=" + MaskSecrets.mask(apiKey)
           +  "}";
    }
}
