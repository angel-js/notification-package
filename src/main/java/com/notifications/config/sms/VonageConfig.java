package com.notifications.config.sms;

import com.notifications.utils.MaskSecrets;
import lombok.Builder;

import java.util.Objects;

@Builder
public record VonageConfig(String accountSid, String authToken) {
    public VonageConfig {
        Objects.requireNonNull(accountSid, "accountSid");
        Objects.requireNonNull(authToken, "authToken");
    }
    @Override
    public String toString() {
        return "VonageConfig{accountSid=" + MaskSecrets.mask(accountSid)
                + " authToken=" + MaskSecrets.mask(authToken) +"}";
    }
}
