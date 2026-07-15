package com.notifications.config.sms;

import com.notifications.utils.MaskSecrets;
import lombok.Builder;

import java.util.Objects;

@Builder
public record TwilioConfig(String accountSid, String authToken) {
    public TwilioConfig {
        Objects.requireNonNull(accountSid, "accountSid");
        Objects.requireNonNull(authToken, "authToken");
    }
    @Override
    public String toString() {
        return "TwilioConfig{accountSid=" + MaskSecrets.mask(accountSid)
                + " authToken=" + MaskSecrets.mask(authToken) +"}";
    }
}
