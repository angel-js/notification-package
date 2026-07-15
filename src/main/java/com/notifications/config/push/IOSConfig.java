package com.notifications.config.push;

import com.notifications.utils.MaskSecrets;
import lombok.Builder;

import java.util.Objects;

@Builder
public record IOSConfig(String apiKey, String appleId) {

    public IOSConfig {
        Objects.requireNonNull(apiKey,"apiKey");
        Objects.requireNonNull(appleId,"appleId");
    }

    @Override
    public String toString() {
        return "IOSConfig{apiKey=" + MaskSecrets.mask(apiKey)
                + " appleId=" + MaskSecrets.mask(appleId) + "}";
    }
}
