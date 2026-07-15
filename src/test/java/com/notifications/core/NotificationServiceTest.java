package com.notifications.core;

import com.notifications.exception.InvalidNotificationException;
import com.notifications.exception.NoSenderRegisteredException;
import com.notifications.model.EmailNotification;
import com.notifications.model.Failure;
import com.notifications.model.NotificationResult;
import com.notifications.model.PushNotification;
import com.notifications.model.SmsNotification;
import com.notifications.model.Success;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService")
class NotificationServiceTest {

    @Mock
    private NotificationSender<EmailNotification> emailSender;

    @Mock
    private NotificationSender<SmsNotification> smsSender;

    @Mock
    private NotificationSender<PushNotification> pushSender;

    private static final EmailNotification EMAIL =
            new EmailNotification("cliente@ejemplo.com", "no-reply@miapp.com", "Bienvenido", "Hola");
    private static final SmsNotification SMS =
            new SmsNotification("+56911111111", "+56922222222", "Tu código es 1234");
    private static final PushNotification PUSH =
            new PushNotification("device-token-123", "Pedido enviado", "Va en camino");

    @Nested
    @DisplayName("despacho por tipo")
    class DespachoPorTipo {

        @Test
        @DisplayName("envía un email por el sender de email")
        void enviaEmailPorElSenderDeEmail() {
            // given un servicio con los tres canales registrados
            when(emailSender.supports()).thenReturn(EmailNotification.class);
            when(smsSender.supports()).thenReturn(SmsNotification.class);
            when(pushSender.supports()).thenReturn(PushNotification.class);
            when(emailSender.send(EMAIL)).thenReturn(Success.of("sg_1"));

            NotificationService service = NotificationService.builder()
                    .register(emailSender)
                    .register(smsSender)
                    .register(pushSender)
                    .build();

            // when se envía un email
            service.send(EMAIL);

            // then solo actúa el sender de email: el tipo del objeto ES el enrutamiento
            verify(emailSender).send(EMAIL);
            verify(smsSender, never()).send(any());
            verify(pushSender, never()).send(any());
        }

        @Test
        @DisplayName("envía un SMS por el sender de SMS")
        void enviaSmsPorElSenderDeSms() {
            when(emailSender.supports()).thenReturn(EmailNotification.class);
            when(smsSender.supports()).thenReturn(SmsNotification.class);
            when(smsSender.send(SMS)).thenReturn(Success.of("tw_1"));

            NotificationService service = NotificationService.builder()
                    .register(emailSender)
                    .register(smsSender)
                    .build();

            service.send(SMS);

            verify(smsSender).send(SMS);
            verify(emailSender, never()).send(any());
        }

        @Test
        @DisplayName("envía un push por el sender de push")
        void enviaPushPorElSenderDePush() {
            when(emailSender.supports()).thenReturn(EmailNotification.class);
            when(pushSender.supports()).thenReturn(PushNotification.class);
            when(pushSender.send(PUSH)).thenReturn(Success.of("fcm_1"));

            NotificationService service = NotificationService.builder()
                    .register(emailSender)
                    .register(pushSender)
                    .build();

            service.send(PUSH);

            verify(pushSender).send(PUSH);
            verify(emailSender, never()).send(any());
        }

        @Test
        @DisplayName("el llamador no elige proveedor: cambiarlo no cambia la llamada")
        void elLlamadorNoEligeProveedor() {
            // given un servicio configurado con un proveedor de email cualquiera
            when(emailSender.supports()).thenReturn(EmailNotification.class);
            when(emailSender.send(EMAIL)).thenReturn(Success.of("proveedor_x_1"));

            NotificationService service = NotificationService.builder()
                    .register(emailSender)
                    .build();

            // when se envía, sin mencionar al proveedor en ningún momento
            NotificationResult resultado = service.send(EMAIL);

            // then el envío ocurre igual: el proveedor es un detalle de configuración
            assertEquals("proveedor_x_1", ((Success) resultado).messageId());
        }
    }

    @Nested
    @DisplayName("propagación del resultado")
    class PropagacionDelResultado {

        @Test
        @DisplayName("devuelve tal cual el Success del sender")
        void devuelveElSuccessDelSender() {
            // given un sender que responde con éxito
            Success esperado = Success.of("sg_123");
            when(emailSender.supports()).thenReturn(EmailNotification.class);
            when(emailSender.send(EMAIL)).thenReturn(esperado);

            NotificationService service = NotificationService.builder().register(emailSender).build();

            // when se envía
            NotificationResult resultado = service.send(EMAIL);

            // then el servicio no altera el resultado, solo lo transporta
            assertSame(esperado, resultado);
        }

        @Test
        @DisplayName("devuelve el Failure del sender SIN lanzar excepción")
        void devuelveElFailureSinLanzar() {
            // given un sender cuyo envío falla
            Failure fallo = new Failure("SENDGRID_ERROR", "El proveedor rechazó el envío");
            when(emailSender.supports()).thenReturn(EmailNotification.class);
            when(emailSender.send(EMAIL)).thenReturn(fallo);

            NotificationService service = NotificationService.builder().register(emailSender).build();

            // when se envía
            NotificationResult resultado = service.send(EMAIL);

            // then el fallo llega como VALOR, no como excepción:
            //      es una condición operativa esperable, no un error de programación
            assertInstanceOf(Failure.class, resultado);
            assertEquals("SENDGRID_ERROR", ((Failure) resultado).errorCode());
        }
    }

    @Nested
    @DisplayName("errores de configuración y de uso")
    class ErroresDeConfiguracion {

        @Test
        @DisplayName("lanza NoSenderRegisteredException si el canal no está registrado")
        void lanzaSiElCanalNoEstaRegistrado() {
            // given un servicio con email pero sin SMS
            when(emailSender.supports()).thenReturn(EmailNotification.class);
            NotificationService service = NotificationService.builder().register(emailSender).build();

            // when se intenta enviar un SMS
            // then falla explícitamente: mejor un error que un envío perdido en silencio
            assertThrows(NoSenderRegisteredException.class, () -> service.send(SMS));
        }

        @Test
        @DisplayName("un servicio sin senders rechaza cualquier envío")
        void servicioSinSendersRechazaTodo() {
            // given un servicio construido sin registrar nada (caso borde)
            NotificationService service = NotificationService.builder().build();

            assertThrows(NoSenderRegisteredException.class, () -> service.send(EMAIL));
        }

        @Test
        @DisplayName("rechaza una notificación nula")
        void rechazaNotificacionNula() {
            when(emailSender.supports()).thenReturn(EmailNotification.class);
            NotificationService service = NotificationService.builder().register(emailSender).build();

            assertThrows(NullPointerException.class, () -> service.send(null));
        }

        @Test
        @DisplayName("rechaza registrar un sender nulo")
        void rechazaRegistrarSenderNulo() {
            // given un builder / when se registra null / then falla al registrar, no al enviar
            assertThrows(NullPointerException.class,
                    () -> NotificationService.builder().register(null));
        }

        @Test
        @DisplayName("falla al construir si se registran dos proveedores del mismo canal")
        void fallaSiSeRegistranDosProveedoresDelMismoCanal() {
            // given dos senders de email (p. ej. SendGrid y Mailgun)
            @SuppressWarnings("unchecked")
            NotificationSender<EmailNotification> otroEmailSender =
                    org.mockito.Mockito.mock(NotificationSender.class);
            when(emailSender.supports()).thenReturn(EmailNotification.class);
            when(otroEmailSender.supports()).thenReturn(EmailNotification.class);

            // when se construye el servicio
            // then falla al CONFIGURAR (fail-fast), no en producción:
            //      enviar por ambos duplicaría cada email
            assertThrows(IllegalStateException.class, () -> NotificationService.builder()
                    .register(emailSender)
                    .register(otroEmailSender)
                    .build());
        }
    }

    @Nested
    @DisplayName("frontera entre excepción y Failure")
    class FronteraExcepcionVsFailure {

        @Test
        @DisplayName("los datos inválidos fallan al construir, antes de llegar al servicio")
        void datosInvalidosFallanAntesDeLlegarAlServicio() {
            // given un email con destino mal formado
            // when se intenta construir la notificación
            // then lanza ANTES de cualquier envío: el sender nunca llega a verlo
            assertThrows(InvalidNotificationException.class,
                    () -> new EmailNotification("no-es-un-email", "a@b.com", "Asunto", "Cuerpo"));

            verify(emailSender, never()).send(any());
        }

        @Test
        @DisplayName("un fallo de envío NO lanza; un error de uso SÍ")
        void falloDeEnvioNoLanzaPeroErrorDeUsoSi() {
            // given un sender de email que falla al enviar
            when(emailSender.supports()).thenReturn(EmailNotification.class);
            when(emailSender.send(EMAIL)).thenReturn(new Failure("TIMEOUT", "Sin respuesta"));
            NotificationService service = NotificationService.builder().register(emailSender).build();

            // when el envío falla -> se devuelve un valor, no se lanza
            NotificationResult resultado = service.send(EMAIL);
            assertInstanceOf(Failure.class, resultado);

            // when el canal no está configurado -> eso SÍ es un error de programación y lanza
            assertThrows(NoSenderRegisteredException.class, () -> service.send(SMS));
        }
    }

    @Nested
    @DisplayName("inmutabilidad del servicio")
    class Inmutabilidad {

        @Test
        @DisplayName("registrar en el builder después de build() no afecta al servicio ya creado")
        void registrarDespuesDeBuildNoAfectaAlServicioYaCreado() {
            // given un servicio ya construido solo con email
            when(emailSender.supports()).thenReturn(EmailNotification.class);
            NotificationService.Builder builder = NotificationService.builder().register(emailSender);
            NotificationService service = builder.build();

            // when alguien sigue usando el builder y añade SMS
            builder.register(smsSender);

            // then el servicio ya creado no cambia: hizo una copia defensiva en build()
            assertThrows(NoSenderRegisteredException.class, () -> service.send(SMS));
        }

        @Test
        @DisplayName("el builder es fluido y encadenable")
        void elBuilderEsFluidoYEncadenable() {
            // given los tres canales
            when(emailSender.supports()).thenReturn(EmailNotification.class);
            when(smsSender.supports()).thenReturn(SmsNotification.class);
            when(pushSender.supports()).thenReturn(PushNotification.class);

            // when se encadenan los register en una sola expresión
            NotificationService service = NotificationService.builder()
                    .register(emailSender)
                    .register(smsSender)
                    .register(pushSender)
                    .build();

            // then el servicio queda operativo para los tres canales
            when(emailSender.send(EMAIL)).thenReturn(Success.of("sg_1"));
            when(smsSender.send(SMS)).thenReturn(Success.of("tw_1"));
            when(pushSender.send(PUSH)).thenReturn(Success.of("fcm_1"));

            assertInstanceOf(Success.class, service.send(EMAIL));
            assertInstanceOf(Success.class, service.send(SMS));
            assertInstanceOf(Success.class, service.send(PUSH));
        }
    }

    @Test
    @DisplayName("el resultado se puede tratar exhaustivamente con switch sobre el tipo sellado")
    void elResultadoSeTrataExhaustivamenteConSwitch() {
        // given un sender que responde con éxito
        when(emailSender.supports()).thenReturn(EmailNotification.class);
        when(emailSender.send(EMAIL)).thenReturn(Success.of("sg_123"));
        NotificationService service = NotificationService.builder().register(emailSender).build();

        // when se consume el resultado con pattern matching, sin rama default
        String descripcion = switch (service.send(EMAIL)) {
            case Success s -> "enviado:" + s.messageId();
            case Failure f -> "fallo:" + f.errorCode();
        };

        // then el sellado permite cubrir todos los casos sin default
        assertTrue(descripcion.startsWith("enviado:"), "Se esperaba un éxito, pero fue: " + descripcion);
        assertEquals("enviado:sg_123", descripcion);
    }
}
