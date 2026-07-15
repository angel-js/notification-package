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

@DisplayName("SmsNotification")
class SmsNotificationTest {

    private static final String FROM = "+56911111111";
    private static final String TO = "+56922222222";
    private static final String MESSAGE = "Tu código de verificación es 1234";

    @Test
    @DisplayName("se construye con datos válidos y expone sus campos")
    void seConstruyeConDatosValidos() {
        // given datos válidos / when se construye
        SmsNotification sms = new SmsNotification(FROM, TO, MESSAGE);

        // then los campos quedan accesibles
        assertEquals(FROM, sms.from());
        assertEquals(TO, sms.to());
        assertEquals(MESSAGE, sms.message());
    }

    @Test
    @DisplayName("es una Notification (participa del tipo sellado)")
    void esUnaNotification() {
        assertInstanceOf(Notification.class, new SmsNotification(FROM, TO, MESSAGE));
    }

    @ParameterizedTest(name = "origen inválido: \"{0}\"")
    @NullSource
    @ValueSource(strings = {
            "",
            "   ",
            "56911111111",          // sin '+'
            "+0911111111",          // empieza en 0
            "+1",                   // demasiado corto
            "+1234567890123456",    // excede los 15 dígitos de E.164
            "+56 9 1111 1111",      // con espacios
            "+56-911111111",        // con guiones
            "telefono"              // texto
    })
    @DisplayName("rechaza un teléfono de origen fuera de E.164")
    void rechazaOrigenFueraDeE164(String origenInvalido) {
        assertThrows(InvalidNotificationException.class,
                () -> new SmsNotification(origenInvalido, TO, MESSAGE));
    }

    @ParameterizedTest(name = "destino inválido: \"{0}\"")
    @NullSource
    @ValueSource(strings = {
            "",
            "   ",
            "56922222222",          // sin '+'
            "+0922222222",          // empieza en 0
            "+1",                   // demasiado corto
            "+1234567890123456",    // excede E.164
            "+56 9 2222 2222"       // con espacios
    })
    @DisplayName("rechaza un teléfono de destino fuera de E.164")
    void rechazaDestinoFueraDeE164(String destinoInvalido) {
        assertThrows(InvalidNotificationException.class,
                () -> new SmsNotification(FROM, destinoInvalido, MESSAGE));
    }

    @ParameterizedTest(name = "mensaje inválido: \"{0}\"")
    @NullSource
    @ValueSource(strings = {"", " ", "   ", "\t"})
    @DisplayName("rechaza un mensaje vacío o en blanco")
    void rechazaMensajeVacio(String mensajeInvalido) {
        assertThrows(InvalidNotificationException.class,
                () -> new SmsNotification(FROM, TO, mensajeInvalido));
    }

    @Test
    @DisplayName("el error distingue el teléfono de origen del de destino")
    void elErrorDistingueOrigenDeDestino() {
        // given un origen inválido y un destino válido
        InvalidNotificationException ex = assertThrows(InvalidNotificationException.class,
                () -> new SmsNotification("roto", TO, MESSAGE));

        // then el mensaje señala el origen, no el destino
        assertTrue(ex.getMessage().contains("origen"),
                "El mensaje debería señalar el origen, pero fue: " + ex.getMessage());
    }

    @ParameterizedTest(name = "E.164 válido: {0}")
    @ValueSource(strings = {
            "+56912345678",         // Chile
            "+14155552671",         // USA
            "+34600000000",         // España
            "+12",                  // límite inferior
            "+123456789012345"      // límite superior (15 dígitos)
    })
    @DisplayName("acepta teléfonos válidos de distintos países y los límites de E.164")
    void aceptaTelefonosValidosDeVariosPaises(String telefono) {
        // given un teléfono E.164 válido usado como destino
        SmsNotification sms = new SmsNotification(FROM, telefono, MESSAGE);

        // then se acepta sin modificarlo
        assertEquals(telefono, sms.to());
    }

    @Test
    @DisplayName("permite que origen y destino sean el mismo número")
    void permiteOrigenIgualADestino() {
        // given un caso borde legítimo: enviarse un SMS a uno mismo
        SmsNotification sms = new SmsNotification(FROM, FROM, MESSAGE);

        assertEquals(sms.from(), sms.to());
    }

    @Test
    @DisplayName("dos SMS con los mismos datos son iguales (semántica de valor)")
    void dosSmsConLosMismosDatosSonIguales() {
        SmsNotification uno = new SmsNotification(FROM, TO, MESSAGE);
        SmsNotification otro = new SmsNotification(FROM, TO, MESSAGE);

        assertEquals(uno, otro);
        assertEquals(uno.hashCode(), otro.hashCode());
    }
}
