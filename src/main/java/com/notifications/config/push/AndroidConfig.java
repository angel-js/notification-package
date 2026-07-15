package com.notifications.config.push;

import com.notifications.utils.MaskSecrets;
import lombok.Builder;

import java.util.Objects;

@Builder
public record AndroidConfig(String apiKey, String serverKey) {

    public AndroidConfig {
        Objects.requireNonNull(apiKey,"apiKey");
        Objects.requireNonNull(serverKey,"serverKey");
    }

    @Override
    public String toString() {
        return "AndroidConfig{apiKey=" + MaskSecrets.mask(apiKey)
                + " serverKey=" + MaskSecrets.mask(serverKey) + "}";
    }
}
