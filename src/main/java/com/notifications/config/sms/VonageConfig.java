package com.notifications.config.sms;

import com.notifications.utils.MaskSecrets;
import lombok.Builder;

import java.util.Objects;

@Builder
public record MovistarConfig(String accountSid, String authToken) {
    public MovistarConfig {
        Objects.requireNonNull(accountSid, "accountSid");
        Objects.requireNonNull(authToken, "authToken");
    }
    @Override
    public String toString() {
        return "MovistarConfig{accountSid=" + MaskSecrets.mask(accountSid)
                + " authToken=" + MaskSecrets.mask(authToken) +"}";
    }
}
