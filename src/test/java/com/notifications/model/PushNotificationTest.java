package com.notifications.model;

import com.notifications.exception.InvalidNotificationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("PushNotification")
class PushNotificationTest {

    private static final String DEVICE_TOKEN = "fMEP0vJqSXY:APA91bHu...token-de-dispositivo";
    private static final String TITLE = "Pedido enviado";
    private static final String BODY = "Tu pedido va en camino";

    @Test
    @DisplayName("se construye con datos válidos y expone sus campos")
    void seConstruyeConDatosValidos() {
        // given datos válidos / when se construye
        PushNotification push = new PushNotification(DEVICE_TOKEN, TITLE, BODY);

        // then los campos quedan accesibles
        assertEquals(DEVICE_TOKEN, push.deviceToken());
        assertEquals(TITLE, push.title());
        assertEquals(BODY, push.body());
    }

    @Test
    @DisplayName("es una Notification (participa del tipo sellado)")
    void esUnaNotification() {
        assertInstanceOf(Notification.class, new PushNotification(DEVICE_TOKEN, TITLE, BODY));
    }

    @ParameterizedTest(name = "token inválido: \"{0}\"")
    @NullSource
    @ValueSource(strings = {"", " ", "   ", "\t", "\n"})
    @DisplayName("rechaza un device token vacío o en blanco")
    void rechazaDeviceTokenVacio(String tokenInvalido) {
        assertThrows(InvalidNotificationException.class,
                () -> new PushNotification(tokenInvalido, TITLE, BODY));
    }

    @ParameterizedTest(name = "título inválido: \"{0}\"")
    @NullSource
    @ValueSource(strings = {"", " ", "   ", "\t"})
    @DisplayName("rechaza un título vacío o en blanco")
    void rechazaTituloVacio(String tituloInvalido) {
        assertThrows(InvalidNotificationException.class,
                () -> new PushNotification(DEVICE_TOKEN, tituloInvalido, BODY));
    }

    @ParameterizedTest(name = "cuerpo inválido: \"{0}\"")
    @NullSource
    @ValueSource(strings = {"", " ", "   ", "\n"})
    @DisplayName("rechaza un cuerpo vacío o en blanco")
    void rechazaCuerpoVacio(String cuerpoInvalido) {
        assertThrows(InvalidNotificationException.class,
                () -> new PushNotification(DEVICE_TOKEN, TITLE, cuerpoInvalido));
    }

    @Test
    @DisplayName("el error de validación identifica el campo que falló")
    void elErrorIdentificaElCampoQueFallo() {
        // given un título vacío
        InvalidNotificationException ex = assertThrows(InvalidNotificationException.class,
                () -> new PushNotification(DEVICE_TOKEN, "", BODY));

        // then el mensaje señala el título y no otro campo
        assertTrue(ex.getMessage().contains("título"),
                "El mensaje debería señalar el título, pero fue: " + ex.getMessage());
    }

    @Test
    @DisplayName("acepta un device token largo como los reales de FCM/APNs")
    void aceptaDeviceTokenLargo() {
        // given un token de longitud realista (los de FCM rondan los 160 caracteres)
        String tokenLargo = "c".repeat(160);

        // when se construye / then se acepta sin truncar
        PushNotification push = new PushNotification(tokenLargo, TITLE, BODY);
        assertEquals(tokenLargo, push.deviceToken());
    }

    @Test
    @DisplayName("acepta emojis en título y cuerpo")
    void aceptaEmojisEnTituloYCuerpo() {
        // given contenido con emojis, habitual en notificaciones push
        PushNotification push = new PushNotification(DEVICE_TOKEN, "¡Pedido enviado! 📦", "Va en camino 🚚");

        assertEquals("¡Pedido enviado! 📦", push.title());
        assertEquals("Va en camino 🚚", push.body());
    }

    @Test
    @DisplayName("dos push con los mismos datos son iguales (semántica de valor)")
    void dosPushConLosMismosDatosSonIguales() {
        PushNotification uno = new PushNotification(DEVICE_TOKEN, TITLE, BODY);
        PushNotification otro = new PushNotification(DEVICE_TOKEN, TITLE, BODY);

        assertEquals(uno, otro);
        assertEquals(uno.hashCode(), otro.hashCode());
    }
}
