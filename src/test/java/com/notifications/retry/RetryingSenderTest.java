package com.notifications.retry;

import com.notifications.core.NotificationSender;
import com.notifications.core.NotificationService;
import com.notifications.model.EmailNotification;
import com.notifications.model.Failure;
import com.notifications.model.NotificationResult;
import com.notifications.model.SmsNotification;
import com.notifications.model.Success;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RetryingSender")
class RetryingSenderTest {

    @Mock
    private NotificationSender<EmailNotification> delegate;

    private static final EmailNotification EMAIL =
            new EmailNotification("cliente@ejemplo.com", "no-reply@miapp.com", "Asunto", "Cuerpo");
    private static final Failure FALLO = new Failure("TIMEOUT", "El proveedor no respondió");

    @Nested
    @DisplayName("número de intentos")
    class NumeroDeIntentos {

        @Test
        @DisplayName("si el primer intento tiene éxito, no reintenta")
        void siElPrimerIntentoTieneExitoNoReintenta() {
            // given un sender que funciona a la primera
            Success exito = Success.of("sg_1");
            when(delegate.send(EMAIL)).thenReturn(exito);
            RetryingSender<EmailNotification> retrying =
                    new RetryingSender<>(delegate, 3, Duration.ZERO);

            // when se envía
            NotificationResult resultado = retrying.send(EMAIL);

            // then se ejecuta UNA sola vez: no se gastan reintentos en un envío que ya funcionó
            verify(delegate, times(1)).send(EMAIL);
            assertSame(exito, resultado);
        }

        @Test
        @DisplayName("maxRetries=3 y fallo permanente ejecuta 4 veces (1 inicial + 3 reintentos)")
        void maxRetriesTresEjecutaCuatroVeces() {
            // given un sender que falla siempre
            when(delegate.send(EMAIL)).thenReturn(FALLO);
            RetryingSender<EmailNotification> retrying =
                    new RetryingSender<>(delegate, 3, Duration.ZERO);

            // when se envía
            retrying.send(EMAIL);

            // then 1 intento inicial + 3 reintentos = 4 ejecuciones
            //      (así queda fijada la semántica: "retry" es lo que ocurre DESPUÉS del primero)
            verify(delegate, times(4)).send(EMAIL);
        }

        @Test
        @DisplayName("maxRetries=0 ejecuta una sola vez (retry desactivado)")
        void maxRetriesCeroEjecutaUnaSolaVez() {
            // given el retry desactivado
            when(delegate.send(EMAIL)).thenReturn(FALLO);
            RetryingSender<EmailNotification> retrying =
                    new RetryingSender<>(delegate, 0, Duration.ZERO);

            // when se envía y falla
            retrying.send(EMAIL);

            // then no reintenta: se comporta como el sender desnudo
            verify(delegate, times(1)).send(EMAIL);
        }

        @Test
        @DisplayName("deja de reintentar en cuanto uno tiene éxito")
        void dejaDeReintentarEnCuantoUnoTieneExito() {
            // given un sender que falla dos veces y acierta a la tercera
            Success exito = Success.of("sg_1");
            when(delegate.send(EMAIL)).thenReturn(FALLO, FALLO, exito);
            RetryingSender<EmailNotification> retrying =
                    new RetryingSender<>(delegate, 5, Duration.ZERO);

            // when se envía
            NotificationResult resultado = retrying.send(EMAIL);

            // then para en la 3ª ejecución: no agota los 5 reintentos disponibles
            verify(delegate, times(3)).send(EMAIL);
            assertSame(exito, resultado);
        }

        @ParameterizedTest(name = "maxRetries={0} -> {0}+1 ejecuciones")
        @ValueSource(ints = {0, 1, 2, 3, 5, 10})
        @DisplayName("ante un fallo permanente siempre ejecuta maxRetries+1 veces")
        void anteFalloPermanenteEjecutaMaxRetriesMasUno(int maxRetries) {
            // given un sender que falla siempre, con distintos maxRetries
            when(delegate.send(EMAIL)).thenReturn(FALLO);
            RetryingSender<EmailNotification> retrying =
                    new RetryingSender<>(delegate, maxRetries, Duration.ZERO);

            // when se envía
            retrying.send(EMAIL);

            // then la relación 1+N se cumple para cualquier valor
            verify(delegate, times(maxRetries + 1)).send(EMAIL);
        }
    }

    @Nested
    @DisplayName("resultado devuelto")
    class ResultadoDevuelto {

        @Test
        @DisplayName("al agotar los reintentos devuelve el último Failure, no una excepción")
        void alAgotarLosReintentosDevuelveElUltimoFailure() {
            // given un sender que falla siempre
            when(delegate.send(EMAIL)).thenReturn(FALLO);
            RetryingSender<EmailNotification> retrying =
                    new RetryingSender<>(delegate, 2, Duration.ZERO);

            // when se agotan los reintentos
            NotificationResult resultado = retrying.send(EMAIL);

            // then sigue siendo un valor: agotar reintentos no convierte el fallo en excepción
            assertInstanceOf(Failure.class, resultado);
        }

        @Test
        @DisplayName("conserva el errorCode original del proveedor")
        void conservaElErrorCodeOriginalDelProveedor() {
            // given un fallo con un código específico del proveedor
            when(delegate.send(EMAIL)).thenReturn(new Failure("SENDGRID_503", "Servicio no disponible"));
            RetryingSender<EmailNotification> retrying =
                    new RetryingSender<>(delegate, 2, Duration.ZERO);

            // when se agotan los reintentos
            Failure resultado = (Failure) retrying.send(EMAIL);

            // then no se envuelve en un error genérico: se conserva la causa real
            assertEquals("SENDGRID_503", resultado.errorCode());
            assertEquals("Servicio no disponible", resultado.message());
        }

        @Test
        @DisplayName("devuelve el Failure del ÚLTIMO intento, no el del primero")
        void devuelveElFailureDelUltimoIntento() {
            // given fallos distintos en cada intento
            when(delegate.send(EMAIL)).thenReturn(
                    new Failure("PRIMERO", "fallo 1"),
                    new Failure("SEGUNDO", "fallo 2"),
                    new Failure("ULTIMO", "fallo 3"));
            RetryingSender<EmailNotification> retrying =
                    new RetryingSender<>(delegate, 2, Duration.ZERO);

            // when se agotan los reintentos
            Failure resultado = (Failure) retrying.send(EMAIL);

            // then se informa del estado más reciente, no de uno obsoleto
            assertEquals("ULTIMO", resultado.errorCode());
        }
    }

    @Nested
    @DisplayName("transparencia del decorator")
    class TransparenciaDelDecorator {

        @Test
        @DisplayName("supports() delega en el sender envuelto")
        void supportsDelegaEnElSenderEnvuelto() {
            // given un decorator sobre un sender de email
            when(delegate.supports()).thenReturn(EmailNotification.class);
            RetryingSender<EmailNotification> retrying =
                    new RetryingSender<>(delegate, 3, Duration.ZERO);

            // when se le pregunta qué soporta
            // then responde lo mismo que el envuelto: por eso el registry lo indexa igual
            //      y el despacho no se entera de que hay reintentos de por medio
            assertEquals(EmailNotification.class, retrying.supports());
        }

        @Test
        @DisplayName("se registra en el servicio como un sender más y despacha bien")
        void seRegistraEnElServicioYDespachaBien() {
            // given un sender envuelto en reintentos, registrado en el servicio
            when(delegate.supports()).thenReturn(EmailNotification.class);
            when(delegate.send(EMAIL)).thenReturn(FALLO, FALLO, Success.of("sg_1"));

            NotificationService service = NotificationService.builder()
                    .register(new RetryingSender<>(delegate, 3, Duration.ZERO))
                    .build();

            // when se envía a través del servicio, sin mencionar el retry
            NotificationResult resultado = service.send(EMAIL);

            // then el servicio despacha y los reintentos ocurren de forma transparente
            assertInstanceOf(Success.class, resultado);
            verify(delegate, times(3)).send(EMAIL);
        }

        @Test
        @DisplayName("el servicio sigue rechazando los canales no registrados")
        void elServicioSigueRechazandoCanalesNoRegistrados() {
            // given un servicio que solo tiene email (con retry)
            when(delegate.supports()).thenReturn(EmailNotification.class);
            NotificationService service = NotificationService.builder()
                    .register(new RetryingSender<>(delegate, 3, Duration.ZERO))
                    .build();

            // when se intenta enviar un SMS
            // then el decorator no altera el despacho por tipo
            assertThrows(com.notifications.exception.NoSenderRegisteredException.class,
                    () -> service.send(new SmsNotification("+56911111111", "+56922222222", "hola")));
        }
    }

    @Nested
    @DisplayName("backoff")
    class Backoff {

        @Test
        @DisplayName("espera entre reintentos y el tiempo crece exponencialmente")
        void esperaEntreReintentosYCreceExponencialmente() {
            // given un backoff inicial de 40 ms y 2 reintentos
            when(delegate.send(EMAIL)).thenReturn(FALLO);
            RetryingSender<EmailNotification> retrying =
                    new RetryingSender<>(delegate, 2, Duration.ofMillis(40));

            // when se agotan los reintentos
            long inicio = System.nanoTime();
            retrying.send(EMAIL);
            long transcurridoMs = (System.nanoTime() - inicio) / 1_000_000;

            // then esperó 40 + 80 = 120 ms como mínimo.
            //      Si el backoff fuera FIJO habrían sido 40 + 40 = 80 ms, así que este
            //      umbral distingue el crecimiento exponencial del backoff constante.
            assertTrue(transcurridoMs >= 120,
                    "Se esperaban al menos 120 ms (40+80) de backoff exponencial, pero fueron " + transcurridoMs);
            verify(delegate, times(3)).send(EMAIL);
        }

        @Test
        @DisplayName("un backoff de cero no introduce espera")
        void backoffDeCeroNoIntroduceEspera() {
            // given un backoff de cero y muchos reintentos
            when(delegate.send(EMAIL)).thenReturn(FALLO);
            RetryingSender<EmailNotification> retrying =
                    new RetryingSender<>(delegate, 10, Duration.ZERO);

            // when se agotan los 10 reintentos
            long inicio = System.nanoTime();
            retrying.send(EMAIL);
            long transcurridoMs = (System.nanoTime() - inicio) / 1_000_000;

            // then los 11 intentos son inmediatos: el corto-circuito del cero evita dormir
            assertTrue(transcurridoMs < 100,
                    "Con Duration.ZERO no debería esperar, pero tardó " + transcurridoMs + " ms");
            verify(delegate, times(11)).send(EMAIL);
        }
    }

    @Nested
    @DisplayName("interrupción del hilo")
    class InterrupcionDelHilo {

        @Test
        @DisplayName("al ser interrumpido durante el backoff aborta y restaura la bandera")
        void alSerInterrumpidoAbortaYRestauraLaBandera() throws InterruptedException {
            // given un sender que falla, con un backoff largo (10 s) para dar tiempo a interrumpir
            when(delegate.send(EMAIL)).thenReturn(FALLO);
            RetryingSender<EmailNotification> retrying =
                    new RetryingSender<>(delegate, 5, Duration.ofSeconds(10));

            AtomicBoolean banderaRestaurada = new AtomicBoolean(false);
            AtomicReference<NotificationResult> resultado = new AtomicReference<>();

            Thread hilo = new Thread(() -> {
                resultado.set(retrying.send(EMAIL));
                // se consulta DESPUÉS de que send() retorne: la bandera debe seguir puesta
                banderaRestaurada.set(Thread.currentThread().isInterrupted());
            });

            // when se interrumpe mientras espera el backoff
            hilo.start();
            Thread.sleep(150);        // dejar que falle el intento 1 y entre al sleep
            hilo.interrupt();
            hilo.join(2_000);         // si tragara la interrupción, seguiría durmiendo 10 s

            // then aborta de inmediato en vez de esperar los 10 s
            assertTrue(!hilo.isAlive(),
                    "El envío debería haber abortado al ser interrumpido, no seguir durmiendo");

            // and la bandera de interrupción queda restaurada, para que quien gobierne el hilo
            //     (p. ej. un Executor apagándose) se entere de que se pidió parar
            assertTrue(banderaRestaurada.get(),
                    "Thread.currentThread().interrupt() debe reponer la bandera que borró el catch");

            // and no siguió reintentando tras la interrupción
            verify(delegate, times(1)).send(EMAIL);
            assertInstanceOf(Failure.class, resultado.get());
        }
    }

    @Nested
    @DisplayName("validación de la configuración")
    class ValidacionDeLaConfiguracion {

        @Test
        @DisplayName("rechaza un delegate nulo")
        void rechazaDelegateNulo() {
            assertThrows(NullPointerException.class,
                    () -> new RetryingSender<>(null, 3, Duration.ZERO));
        }

        @ParameterizedTest(name = "maxRetries={0}")
        @ValueSource(ints = {-1, -5, Integer.MIN_VALUE})
        @DisplayName("rechaza un maxRetries negativo al construir")
        void rechazaMaxRetriesNegativo(int negativo) {
            // given un maxRetries sin sentido
            // when se construye
            // then falla al CONFIGURAR, en vez de aceptarlo en silencio y dejar el retry apagado
            assertThrows(IllegalArgumentException.class,
                    () -> new RetryingSender<>(delegate, negativo, Duration.ZERO));
        }

        @Test
        @DisplayName("rechaza un backoff nulo al construir, no al enviar")
        void rechazaBackoffNuloAlConstruir() {
            // given un backoff nulo
            // when se construye
            // then falla aquí mismo. Sin esta validación el NPE saltaría dentro de send(),
            //      en producción y después de haber enviado ya una vez
            assertThrows(NullPointerException.class,
                    () -> new RetryingSender<>(delegate, 3, null));
        }

        @Test
        @DisplayName("rechaza un backoff negativo al construir")
        void rechazaBackoffNegativo() {
            assertThrows(IllegalArgumentException.class,
                    () -> new RetryingSender<>(delegate, 3, Duration.ofMillis(-100)));
        }

        @Test
        @DisplayName("el error de maxRetries negativo indica el valor recibido")
        void elErrorDeMaxRetriesIndicaElValorRecibido() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> new RetryingSender<>(delegate, -7, Duration.ZERO));

            assertTrue(ex.getMessage().contains("-7"),
                    "El mensaje debería incluir el valor recibido, pero fue: " + ex.getMessage());
        }

        @Test
        @DisplayName("acepta maxRetries=0 como configuración válida")
        void aceptaMaxRetriesCeroComoValido() {
            // given maxRetries=0: desactivar el retry es legítimo, no un error
            when(delegate.send(EMAIL)).thenReturn(FALLO);
            RetryingSender<EmailNotification> retrying =
                    new RetryingSender<>(delegate, 0, Duration.ZERO);

            assertInstanceOf(Failure.class, retrying.send(EMAIL));
            verify(delegate, times(1)).send(EMAIL);
        }
    }
}
