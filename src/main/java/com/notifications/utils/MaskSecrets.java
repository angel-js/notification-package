package com.notifications.utils;

public final class MaskSecrets {
    private MaskSecrets() {}

    /** Devuelve una versión segura de un secreto: nunca revela su contenido. */
    public static String mask(String secret) {
        return (secret == null || secret.isBlank()) ? "<null>" : "****";
    }
}
