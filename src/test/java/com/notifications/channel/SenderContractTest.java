package com.notifications.channel;

import com.notifications.channel.email.MailgunSender;
import com.notifications.channel.email.SendGridSender;
import com.notifications.channel.push.AndroidSender;
import com.notifications.channel.push.IOSSender;
import com.notifications.channel.sms.MovistarSender;
import com.notifications.channel.sms.TwilioSender;
import com.notifications.config.email.MailGunConfig;
import com.notifications.config.email.SendGridConfig;
import com.notifications.config.push.AndroidConfig;
import com.notifications.config.push.IOSConfig;
import com.notifications.config.sms.MovistarConfig;
import com.notifications.config.sms.TwilioConfig;
import com.notifications.core.NotificationSender;
import com.notifications.core.NotificationService;
import com.notifications.model.EmailNotification;
import com.notifications.model.Notification;
import com.notifications.model.NotificationResult;
import com.notifications.model.PushNotification;
import com.notifications.model.SmsNotification;
import com.notifications.model.Success;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contrato común que deben cumplir TODOS los senders, sea cual sea su proveedor.
 * Si mañana se añade un sender nuevo, basta con sumarlo al MethodSource.
 */
@DisplayName("Contrato de los senders")
class SenderContractTest {

    private static final EmailNotification EMAIL =
            new EmailNotification("cliente@ejemplo.com", "no-reply@miapp.com", "Bienvenido", "Hola");
    private static final SmsNotification SMS =
            new SmsNotification("+56911111111", "+56922222222", "Tu código es 1234");
    private static final PushNotification PUSH =
            new PushNotification("device-token-123", "Pedido enviado", "Va en camino");

    static Stream<Arguments> todosLosSenders() {
        return Stream.of(
                Arguments.of("SendGridSender",
                        new SendGridSender(new SendGridConfig("api-key")),
                        EmailNotification.class, EMAIL),
                Arguments.of("MailgunSender",
                        new MailgunSender(MailGunConfig.builder().apiKey("api-key").domain("mg.app.com").build()),
                        EmailNotification.class, EMAIL),
                Arguments.of("TwilioSender",
                        new TwilioSender(TwilioConfig.builder().accountSid("AC1").authToken("tok").build()),
                        SmsNotification.class, SMS),
                Arguments.of("MovistarSender",
                        new MovistarSender(MovistarConfig.builder().accountSid("MV1").authToken("tok").build()),
                        SmsNotification.class, SMS),
                Arguments.of("AndroidSender",
                        new AndroidSender(AndroidConfig.builder().apiKey("key").serverKey("srv").build()),
                        PushNotification.class, PUSH),
                Arguments.of("IOSSender",
                        new IOSSender(IOSConfig.builder().apiKey("key").appleId("apple-id").build()),
                        PushNotification.class, PUSH)
        );
    }

    @ParameterizedTest(name = "{0} declara el tipo que atiende")
    @MethodSource("todosLosSenders")
    @DisplayName("supports() declara el tipo de notificación que el sender atiende")
    void supportsDeclaraElTipoQueAtiende(String nombre, NotificationSender<?> sender,
                                         Class<?> tipoEsperado, Notification notificacion) {
        // given un sender / when se le pregunta qué soporta
        // then declara su tipo: es la clave con la que el servicio lo indexa
        assertEquals(tipoEsperado, sender.supports(),
                nombre + " debería declarar " + tipoEsperado.getSimpleName());
    }

    @ParameterizedTest(name = "{0} devuelve Success al enviar")
    @MethodSource("todosLosSenders")
    @DisplayName("un envío correcto devuelve Success")
    void unEnvioCorrectoDevuelveSuccess(String nombre, NotificationSender<?> sender,
                                        Class<?> tipoEsperado, Notification notificacion) {
        // given un sender registrado en el servicio
        NotificationService service = NotificationService.builder().register(sender).build();

        // when se envía una notificación válida de su tipo
        NotificationResult resultado = service.send(notificacion);

        // then el envío simulado tiene éxito
        assertInstanceOf(Success.class, resultado, nombre + " debería devolver Success");
    }

    @ParameterizedTest(name = "{0} genera un messageId utilizable")
    @MethodSource("todosLosSenders")
    @DisplayName("el Success trae un messageId no vacío y un timestamp")
    void elSuccessTraeMessageIdYTimestamp(String nombre, NotificationSender<?> sender,
                                          Class<?> tipoEsperado, Notification notificacion) {
        // given un sender / when envía correctamente
        NotificationService service = NotificationService.builder().register(sender).build();
        Success success = (Success) service.send(notificacion);

        // then el resultado permite rastrear el envío
        assertNotNull(success.messageId(), nombre + " debería generar un messageId");
        assertTrue(!success.messageId().isBlank(), nombre + " no debería devolver un messageId vacío");
        assertNotNull(success.timestamp(), nombre + " debería registrar cuándo se envió");
    }

    @ParameterizedTest(name = "{0} genera un messageId distinto por envío")
    @MethodSource("todosLosSenders")
    @DisplayName("cada envío genera un messageId único")
    void cadaEnvioGeneraUnMessageIdUnico(String nombre, NotificationSender<?> sender,
                                         Class<?> tipoEsperado, Notification notificacion) {
        // given un mismo sender enviando dos veces la misma notificación
        NotificationService service = NotificationService.builder().register(sender).build();

        Success primero = (Success) service.send(notificacion);
        Success segundo = (Success) service.send(notificacion);

        // then cada envío es rastreable por separado
        assertNotEquals(primero.messageId(), segundo.messageId(),
                nombre + " debería generar un id distinto en cada envío");
    }

    @Nested
    @DisplayName("intercambiabilidad de proveedores")
    class IntercambiabilidadDeProveedores {

        @Test
        @DisplayName("cambiar de proveedor de email no cambia la llamada de envío")
        void cambiarProveedorDeEmailNoCambiaLaLlamada() {
            // given el mismo email enviado con dos proveedores distintos
            NotificationService conSendGrid = NotificationService.builder()
                    .register(new SendGridSender(new SendGridConfig("key")))
                    .build();
            NotificationService conMailgun = NotificationService.builder()
                    .register(new MailgunSender(
                            MailGunConfig.builder().apiKey("key").domain("mg.app.com").build()))
                    .build();

            // when se envía exactamente la misma notificación por ambos
            NotificationResult porSendGrid = conSendGrid.send(EMAIL);
            NotificationResult porMailgun = conMailgun.send(EMAIL);

            // then ambos funcionan con la misma llamada: el proveedor es intercambiable
            assertInstanceOf(Success.class, porSendGrid);
            assertInstanceOf(Success.class, porMailgun);
        }

        @Test
        @DisplayName("cambiar de proveedor de SMS no cambia la llamada de envío")
        void cambiarProveedorDeSmsNoCambiaLaLlamada() {
            NotificationService conTwilio = NotificationService.builder()
                    .register(new TwilioSender(
                            TwilioConfig.builder().accountSid("AC1").authToken("tok").build()))
                    .build();
            NotificationService conMovistar = NotificationService.builder()
                    .register(new MovistarSender(
                            MovistarConfig.builder().accountSid("MV1").authToken("tok").build()))
                    .build();

            assertInstanceOf(Success.class, conTwilio.send(SMS));
            assertInstanceOf(Success.class, conMovistar.send(SMS));
        }

        @Test
        @DisplayName("los tres canales funcionan a la vez en un mismo servicio")
        void losTresCanalesFuncionanALaVez() {
            // given un servicio con un proveedor por cada canal
            NotificationService service = NotificationService.builder()
                    .register(new SendGridSender(new SendGridConfig("key")))
                    .register(new TwilioSender(
                            TwilioConfig.builder().accountSid("AC1").authToken("tok").build()))
                    .register(new AndroidSender(
                            AndroidConfig.builder().apiKey("key").serverKey("srv").build()))
                    .build();

            // when se envía por los tres
            // then cada notificación llega a su canal sin que el llamador lo indique
            assertInstanceOf(Success.class, service.send(EMAIL));
            assertInstanceOf(Success.class, service.send(SMS));
            assertInstanceOf(Success.class, service.send(PUSH));
        }
    }
}
