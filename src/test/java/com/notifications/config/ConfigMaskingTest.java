package com.notifications.config;

import com.notifications.config.email.MailGunConfig;
import com.notifications.config.email.SendGridConfig;
import com.notifications.config.push.AndroidConfig;
import com.notifications.config.push.IOSConfig;
import com.notifications.config.sms.MovistarConfig;
import com.notifications.config.sms.TwilioConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica el requisito de seguridad: el toString() de una config NUNCA debe
 * exponer credenciales. Es el test más importante de esta capa, porque un fallo
 * aquí no rompe nada... hasta que una API key aparece en un log de producción.
 */
@DisplayName("Enmascarado de credenciales en las configs")
class ConfigMaskingTest {

    /** Secretos reconocibles: si alguno aparece en un toString(), el test falla. */
    private static final String SECRETO_1 = "SG-super-secreto-no-debe-aparecer-123";
    private static final String SECRETO_2 = "token-ultra-secreto-tampoco-456";

    /** Cada caso: nombre del proveedor, su toString(), y los secretos que NO deben filtrarse. */
    static Stream<org.junit.jupiter.params.provider.Arguments> configsConSecretos() {
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of(
                        "SendGridConfig",
                        new SendGridConfig(SECRETO_1).toString(),
                        new String[]{SECRETO_1}),
                org.junit.jupiter.params.provider.Arguments.of(
                        "MailGunConfig",
                        MailGunConfig.builder().apiKey(SECRETO_1).domain("mg.miapp.com").build().toString(),
                        new String[]{SECRETO_1}),
                org.junit.jupiter.params.provider.Arguments.of(
                        "TwilioConfig",
                        TwilioConfig.builder().accountSid(SECRETO_1).authToken(SECRETO_2).build().toString(),
                        new String[]{SECRETO_1, SECRETO_2}),
                org.junit.jupiter.params.provider.Arguments.of(
                        "MovistarConfig",
                        MovistarConfig.builder().accountSid(SECRETO_1).authToken(SECRETO_2).build().toString(),
                        new String[]{SECRETO_1, SECRETO_2}),
                org.junit.jupiter.params.provider.Arguments.of(
                        "AndroidConfig",
                        AndroidConfig.builder().apiKey(SECRETO_1).serverKey(SECRETO_2).build().toString(),
                        new String[]{SECRETO_1, SECRETO_2}),
                org.junit.jupiter.params.provider.Arguments.of(
                        "IOSConfig",
                        IOSConfig.builder().apiKey(SECRETO_1).appleId(SECRETO_2).build().toString(),
                        new String[]{SECRETO_1, SECRETO_2})
        );
    }

    @ParameterizedTest(name = "{0} no filtra sus credenciales")
    @MethodSource("configsConSecretos")
    @DisplayName("ninguna config expone sus credenciales en toString()")
    void ningunaConfigExponeSusCredenciales(String nombre, String salida, String[] secretos) {
        // given una config construida con credenciales reales
        // when se convierte a String (lo que ocurre al loggearla o depurarla)
        // then ningún secreto aparece en la salida
        for (String secreto : secretos) {
            assertTrue(!salida.contains(secreto),
                    nombre + " filtró una credencial en toString(): " + salida);
        }
    }

    @ParameterizedTest(name = "{0} marca las credenciales como enmascaradas")
    @MethodSource("configsConSecretos")
    @DisplayName("toString() indica que hay credenciales, pero enmascaradas")
    void toStringMarcaLasCredencialesComoEnmascaradas(String nombre, String salida, String[] secretos) {
        // given una config con credenciales
        // when se convierte a String
        // then aparece la marca de enmascarado: se ve que hay secreto, pero no cuál
        assertTrue(salida.contains("****"),
                nombre + " debería mostrar '****' donde van las credenciales: " + salida);
    }

    @ParameterizedTest(name = "{0} se identifica en su toString()")
    @MethodSource("configsConSecretos")
    @DisplayName("toString() identifica de qué config se trata (sigue siendo útil para depurar)")
    void toStringIdentificaLaConfig(String nombre, String salida, String[] secretos) {
        // given cualquier config
        // when se convierte a String
        // then el enmascarado no la vuelve inútil: aún se sabe qué config es
        assertTrue(salida.contains("Config"),
                nombre + " debería identificarse en su toString(): " + salida);
    }

    @Nested
    @DisplayName("los datos no sensibles sí se muestran")
    class DatosNoSensibles {

        @Test
        @DisplayName("MailGunConfig muestra el dominio, que no es una credencial")
        void mailgunMuestraElDominio() {
            // given un dominio, que no es secreto y ayuda a depurar
            String salida = MailGunConfig.builder()
                    .apiKey(SECRETO_1)
                    .domain("mg.miapp.com")
                    .build()
                    .toString();

            // then se muestra tal cual, a diferencia de la apiKey
            assertTrue(salida.contains("mg.miapp.com"),
                    "El dominio no es una credencial y debería verse: " + salida);
            assertTrue(!salida.contains(SECRETO_1),
                    "La apiKey sí es una credencial y no debería verse: " + salida);
        }
    }

    @Nested
    @DisplayName("construcción y validación")
    class ConstruccionYValidacion {

        @Test
        @DisplayName("el builder produce una config con todos sus campos")
        void elBuilderProduceLaConfigCompleta() {
            // given un builder con todos los campos
            TwilioConfig config = TwilioConfig.builder()
                    .accountSid("AC123")
                    .authToken("token123")
                    .build();

            // then los valores llegan intactos al record
            assertEquals("AC123", config.accountSid());
            assertEquals("token123", config.authToken());
        }

        @Test
        @DisplayName("SendGridConfig rechaza una apiKey nula")
        void sendGridRechazaApiKeyNula() {
            assertThrows(NullPointerException.class, () -> new SendGridConfig(null));
        }

        @Test
        @DisplayName("MailGunConfig rechaza campos nulos")
        void mailgunRechazaCamposNulos() {
            assertThrows(NullPointerException.class,
                    () -> MailGunConfig.builder().apiKey(null).domain("d.com").build());
            assertThrows(NullPointerException.class,
                    () -> MailGunConfig.builder().apiKey("k").domain(null).build());
        }

        @Test
        @DisplayName("TwilioConfig rechaza campos nulos")
        void twilioRechazaCamposNulos() {
            assertThrows(NullPointerException.class,
                    () -> TwilioConfig.builder().accountSid(null).authToken("t").build());
            assertThrows(NullPointerException.class,
                    () -> TwilioConfig.builder().accountSid("s").authToken(null).build());
        }

        @Test
        @DisplayName("un builder incompleto falla al construir, no más tarde")
        void builderIncompletoFallaAlConstruir() {
            // given un builder al que le falta el authToken
            // when se llama a build() / then falla ahí mismo, no al enviar
            assertThrows(NullPointerException.class,
                    () -> TwilioConfig.builder().accountSid("AC123").build());
        }
    }
}
