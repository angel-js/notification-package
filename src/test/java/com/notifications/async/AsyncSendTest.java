package com.notifications.async;

import com.notifications.core.NotificationSender;
import com.notifications.core.NotificationService;
import com.notifications.exception.NoSenderRegisteredException;
import com.notifications.model.EmailNotification;
import com.notifications.model.Failure;
import com.notifications.model.Notification;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Envío asíncrono")
class AsyncSendTest {

    @Mock
    private NotificationSender<EmailNotification> emailSender;

    @Mock
    private NotificationSender<SmsNotification> smsSender;

    private static final EmailNotification EMAIL =
            new EmailNotification("cliente@ejemplo.com", "no-reply@miapp.com", "Asunto", "Cuerpo");
    private static final SmsNotification SMS =
            new SmsNotification("+56911111111", "+56922222222", "Mensaje");
    private static final PushNotification PUSH =
            new PushNotification("token", "Título", "Cuerpo");

    /** Executor síncrono que cuenta las tareas recibidas: sirve para probar que SE USA. */
    private static final class ExecutorContador implements Executor {
        private final AtomicInteger tareas = new AtomicInteger();

        @Override
        public void execute(Runnable command) {
            tareas.incrementAndGet();
            command.run();
        }

        int tareasRecibidas() {
            return tareas.get();
        }
    }

    @Nested
    @DisplayName("sendAsync")
    class SendAsync {

        @Test
        @DisplayName("completa el futuro con el Success del sender")
        void completaElFuturoConElSuccess() throws Exception {
            // given un sender que responde con éxito
            Success exito = Success.of("sg_1");
            when(emailSender.supports()).thenReturn(EmailNotification.class);
            when(emailSender.send(EMAIL)).thenReturn(exito);
            NotificationService service = NotificationService.builder().register(emailSender).build();

            // when se envía de forma asíncrona
            CompletableFuture<NotificationResult> futuro = service.sendAsync(EMAIL);

            // then el futuro acaba trayendo el mismo resultado que el envío síncrono
            assertSame(exito, futuro.get(5, TimeUnit.SECONDS));
            verify(emailSender).send(EMAIL);
        }

        @Test
        @DisplayName("un fallo de envío completa el futuro con Failure, NO excepcionalmente")
        void unFalloDeEnvioCompletaConFailure() throws Exception {
            // given un sender cuyo envío falla
            when(emailSender.supports()).thenReturn(EmailNotification.class);
            when(emailSender.send(EMAIL)).thenReturn(new Failure("TIMEOUT", "sin respuesta"));
            NotificationService service = NotificationService.builder().register(emailSender).build();

            // when se envía de forma asíncrona
            CompletableFuture<NotificationResult> futuro = service.sendAsync(EMAIL);
            NotificationResult resultado = futuro.get(5, TimeUnit.SECONDS);

            // then el futuro se completa NORMALMENTE con un Failure:
            //      la regla "fallo de envío = valor" se mantiene también en asíncrono
            assertTrue(!futuro.isCompletedExceptionally(),
                    "Un fallo de envío no debe completar el futuro excepcionalmente");
            assertInstanceOf(Failure.class, resultado);
            assertEquals("TIMEOUT", ((Failure) resultado).errorCode());
        }

        @Test
        @DisplayName("un canal sin sender completa el futuro excepcionalmente")
        void canalSinSenderCompletaExcepcionalmente() {
            // given un servicio sin sender de SMS
            when(emailSender.supports()).thenReturn(EmailNotification.class);
            NotificationService service = NotificationService.builder().register(emailSender).build();

            // when se intenta enviar un SMS de forma asíncrona
            CompletableFuture<NotificationResult> futuro = service.sendAsync(SMS);

            // then el error de configuración NO se pierde: viaja envuelto en el futuro
            ExecutionException ex = assertThrows(ExecutionException.class,
                    () -> futuro.get(5, TimeUnit.SECONDS));
            assertInstanceOf(NoSenderRegisteredException.class, ex.getCause());
        }

        @Test
        @DisplayName("usa el Executor configurado en el builder")
        void usaElExecutorConfigurado() throws Exception {
            // given un executor propio inyectado en el builder
            ExecutorContador executor = new ExecutorContador();
            when(emailSender.supports()).thenReturn(EmailNotification.class);
            when(emailSender.send(EMAIL)).thenReturn(Success.of("sg_1"));

            NotificationService service = NotificationService.builder()
                    .register(emailSender)
                    .executor(executor)
                    .build();

            // when se envía de forma asíncrona
            service.sendAsync(EMAIL).get(5, TimeUnit.SECONDS);

            // then el envío pasó por NUESTRO executor, no por el pool por defecto
            assertEquals(1, executor.tareasRecibidas(),
                    "El envío asíncrono debería ejecutarse en el Executor configurado");
        }

        @Test
        @DisplayName("funciona sin configurar Executor (usa el pool común por defecto)")
        void funcionaSinConfigurarExecutor() throws Exception {
            // given un servicio SIN llamar a .executor(...)
            when(emailSender.supports()).thenReturn(EmailNotification.class);
            when(emailSender.send(EMAIL)).thenReturn(Success.of("sg_1"));
            NotificationService service = NotificationService.builder().register(emailSender).build();

            // when se envía de forma asíncrona
            // then funciona igual: el default evita que el usuario tenga que crear
            //      y apagar un pool solo para poder usar la librería
            assertInstanceOf(Success.class, service.sendAsync(EMAIL).get(5, TimeUnit.SECONDS));
        }

        @Test
        @DisplayName("no bloquea el hilo llamante")
        void noBloqueaElHiloLlamante() throws Exception {
            // given un sender lento (200 ms) sobre un pool real
            CountDownLatch puedeTerminar = new CountDownLatch(1);
            when(emailSender.supports()).thenReturn(EmailNotification.class);
            when(emailSender.send(EMAIL)).thenAnswer(inv -> {
                puedeTerminar.await(5, TimeUnit.SECONDS);
                return Success.of("sg_1");
            });

            ExecutorService pool = Executors.newSingleThreadExecutor();
            try {
                NotificationService service = NotificationService.builder()
                        .register(emailSender)
                        .executor(pool)
                        .build();

                // when se lanza el envío
                CompletableFuture<NotificationResult> futuro = service.sendAsync(EMAIL);

                // then sendAsync ya retornó aunque el envío sigue en curso
                assertTrue(!futuro.isDone(),
                        "sendAsync no debería esperar a que el envío termine");

                // and al liberar al sender, el futuro se completa
                puedeTerminar.countDown();
                assertInstanceOf(Success.class, futuro.get(5, TimeUnit.SECONDS));
            } finally {
                pool.shutdownNow();
            }
        }

        @Test
        @DisplayName("rechaza una notificación nula")
        void rechazaNotificacionNula() {
            NotificationService service = NotificationService.builder().build();
            assertThrows(NullPointerException.class, () -> service.sendAsync(null));
        }
    }

    @Nested
    @DisplayName("sendBatch")
    class SendBatch {

        @Test
        @DisplayName("envía todas las notificaciones del lote")
        void enviaTodasLasNotificacionesDelLote() throws Exception {
            // given un lote de tres emails
            when(emailSender.supports()).thenReturn(EmailNotification.class);
            when(emailSender.send(EMAIL)).thenReturn(Success.of("sg_1"));
            NotificationService service = NotificationService.builder().register(emailSender).build();

            // when se envía el lote
            List<NotificationResult> resultados =
                    service.sendBatch(List.of(EMAIL, EMAIL, EMAIL)).get(5, TimeUnit.SECONDS);

            // then se envían las tres y se devuelven tres resultados
            assertEquals(3, resultados.size());
            verify(emailSender, times(3)).send(EMAIL);
        }

        @Test
        @DisplayName("mezcla canales distintos en un mismo lote")
        void mezclaCanalesDistintosEnUnMismoLote() throws Exception {
            // given un lote con email y SMS mezclados
            when(emailSender.supports()).thenReturn(EmailNotification.class);
            when(smsSender.supports()).thenReturn(SmsNotification.class);
            when(emailSender.send(EMAIL)).thenReturn(Success.of("sg_1"));
            when(smsSender.send(SMS)).thenReturn(Success.of("tw_1"));

            NotificationService service = NotificationService.builder()
                    .register(emailSender)
                    .register(smsSender)
                    .build();

            // when se envía el lote mezclado
            List<NotificationResult> resultados =
                    service.sendBatch(List.of(EMAIL, SMS)).get(5, TimeUnit.SECONDS);

            // then cada notificación se despachó a su canal
            assertEquals(2, resultados.size());
            verify(emailSender).send(EMAIL);
            verify(smsSender).send(SMS);
        }

        @Test
        @DisplayName("los resultados conservan el orden de la lista de entrada")
        void losResultadosConservanElOrdenDeEntrada() throws Exception {
            // given un lote donde el PRIMERO tarda más que el segundo:
            //      si se devolviera por orden de finalización, saldrían al revés
            when(emailSender.supports()).thenReturn(EmailNotification.class);
            EmailNotification lento = new EmailNotification("lento@x.com", "a@b.com", "S", "B");
            EmailNotification rapido = new EmailNotification("rapido@x.com", "a@b.com", "S", "B");

            when(emailSender.send(lento)).thenAnswer(inv -> {
                Thread.sleep(120);
                return Success.of("LENTO");
            });
            when(emailSender.send(rapido)).thenReturn(Success.of("RAPIDO"));

            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                NotificationService service = NotificationService.builder()
                        .register(emailSender)
                        .executor(pool)
                        .build();

                // when se envía [lento, rapido]
                List<NotificationResult> resultados =
                        service.sendBatch(List.of(lento, rapido)).get(5, TimeUnit.SECONDS);

                // then el orden es el de ENTRADA, para poder correlacionar cada resultado
                //      con la notificación que lo originó
                assertEquals("LENTO", ((Success) resultados.get(0)).messageId());
                assertEquals("RAPIDO", ((Success) resultados.get(1)).messageId());
            } finally {
                pool.shutdownNow();
            }
        }

        @Test
        @DisplayName("un lote vacío devuelve una lista vacía sin enviar nada")
        void loteVacioDevuelveListaVacia() throws Exception {
            // given un lote vacío (caso borde)
            NotificationService service = NotificationService.builder().build();

            // when se envía
            List<NotificationResult> resultados = service.sendBatch(List.of()).get(5, TimeUnit.SECONDS);

            // then no falla ni se cuelga: simplemente no hay nada que enviar
            assertTrue(resultados.isEmpty(), "Un lote vacío debería devolver una lista vacía");
        }

        @Test
        @DisplayName("mezcla Success y Failure en el mismo lote")
        void mezclaSuccessYFailureEnElMismoLote() throws Exception {
            // given un lote donde un envío va bien y otro falla
            when(emailSender.supports()).thenReturn(EmailNotification.class);
            EmailNotification ok = new EmailNotification("ok@x.com", "a@b.com", "S", "B");
            EmailNotification ko = new EmailNotification("ko@x.com", "a@b.com", "S", "B");
            when(emailSender.send(ok)).thenReturn(Success.of("sg_1"));
            when(emailSender.send(ko)).thenReturn(new Failure("BOUNCED", "rebotado"));

            NotificationService service = NotificationService.builder().register(emailSender).build();

            // when se envía el lote
            List<NotificationResult> resultados =
                    service.sendBatch(List.of(ok, ko)).get(5, TimeUnit.SECONDS);

            // then un fallo NO tumba el lote: cada envío reporta lo suyo
            assertInstanceOf(Success.class, resultados.get(0));
            assertInstanceOf(Failure.class, resultados.get(1));
        }

        @Test
        @DisplayName("un canal sin sender completa el lote excepcionalmente")
        void canalSinSenderCompletaElLoteExcepcionalmente() {
            // given un lote que incluye un canal no registrado
            when(emailSender.supports()).thenReturn(EmailNotification.class);
            NotificationService service = NotificationService.builder().register(emailSender).build();

            // when se envía el lote con un push sin sender
            CompletableFuture<List<NotificationResult>> futuro =
                    service.sendBatch(List.of(EMAIL, PUSH));

            // then el error de configuración se hace notar en vez de esconderse
            //      entre los resultados correctos
            ExecutionException ex = assertThrows(ExecutionException.class,
                    () -> futuro.get(5, TimeUnit.SECONDS));
            assertInstanceOf(NoSenderRegisteredException.class, ex.getCause());
        }

        @Test
        @DisplayName("lanza todos los envíos sobre el Executor configurado")
        void lanzaTodosLosEnviosSobreElExecutorConfigurado() throws Exception {
            // given un lote de 5 y un executor propio
            ExecutorContador executor = new ExecutorContador();
            when(emailSender.supports()).thenReturn(EmailNotification.class);
            when(emailSender.send(EMAIL)).thenReturn(Success.of("sg_1"));

            NotificationService service = NotificationService.builder()
                    .register(emailSender)
                    .executor(executor)
                    .build();

            // when se envía el lote
            service.sendBatch(List.of(EMAIL, EMAIL, EMAIL, EMAIL, EMAIL)).get(5, TimeUnit.SECONDS);

            // then cada envío fue una tarea independiente en el executor
            assertEquals(5, executor.tareasRecibidas(),
                    "Cada notificación del lote debería ser una tarea en el Executor");
        }

        @Test
        @DisplayName("los envíos del lote corren en paralelo, no en serie")
        void losEnviosDelLoteCorrenEnParalelo() throws Exception {
            // given 3 envíos que solo terminan cuando los 3 han empezado:
            //      si se ejecutaran en serie, esto se quedaría bloqueado para siempre
            CountDownLatch todosEmpezaron = new CountDownLatch(3);
            when(emailSender.supports()).thenReturn(EmailNotification.class);
            when(emailSender.send(EMAIL)).thenAnswer(inv -> {
                todosEmpezaron.countDown();
                boolean arrancaronTodos = todosEmpezaron.await(3, TimeUnit.SECONDS);
                return arrancaronTodos ? Success.of("ok") : new Failure("SERIE", "no hubo paralelismo");
            });

            ExecutorService pool = Executors.newFixedThreadPool(3);
            try {
                NotificationService service = NotificationService.builder()
                        .register(emailSender)
                        .executor(pool)
                        .build();

                // when se envía el lote de 3
                List<NotificationResult> resultados =
                        service.sendBatch(List.of(EMAIL, EMAIL, EMAIL)).get(5, TimeUnit.SECONDS);

                // then los 3 estuvieron vivos a la vez
                assertTrue(resultados.stream().allMatch(r -> r instanceof Success),
                        "Los envíos del lote deberían solaparse en el tiempo: " + resultados);
            } finally {
                pool.shutdownNow();
            }
        }

        @Test
        @DisplayName("rechaza una lista nula")
        void rechazaListaNula() {
            NotificationService service = NotificationService.builder().build();
            assertThrows(NullPointerException.class, () -> service.sendBatch(null));
        }
    }

    @Nested
    @DisplayName("AsyncDispatcher")
    class DispatcherDirecto {

        @Test
        @DisplayName("rechaza un Executor nulo")
        void rechazaExecutorNulo() {
            assertThrows(NullPointerException.class, () -> new AsyncDispatcher(null));
        }

        @Test
        @DisplayName("el builder rechaza un Executor nulo")
        void elBuilderRechazaExecutorNulo() {
            assertThrows(NullPointerException.class,
                    () -> NotificationService.builder().executor(null));
        }

        @Test
        @DisplayName("no conoce los canales: solo ejecuta la función de envío que recibe")
        void noConoceLosCanalesSoloEjecutaLaFuncion() throws Exception {
            // given un dispatcher y una función de envío cualquiera
            AsyncDispatcher dispatcher = new AsyncDispatcher(Runnable::run);
            List<Notification> enviadas = new ArrayList<>();
            Success exito = Success.of("id_1");

            // when se le pasa la operación como función
            CompletableFuture<NotificationResult> futuro =
                    dispatcher.sendAsync(EMAIL, n -> {
                        enviadas.add(n);
                        return exito;
                    });

            // then ejecuta esa función sin saber nada de senders ni de registry:
            //      su única responsabilidad es la concurrencia
            assertSame(exito, futuro.get(5, TimeUnit.SECONDS));
            assertEquals(List.of(EMAIL), enviadas);
        }

        @Test
        @DisplayName("propaga como CompletionException lo que lance la función de envío")
        void propagaLoQueLanceLaFuncionDeEnvio() {
            // given una función de envío que revienta
            AsyncDispatcher dispatcher = new AsyncDispatcher(Runnable::run);

            // when se ejecuta
            CompletableFuture<NotificationResult> futuro =
                    dispatcher.sendAsync(EMAIL, n -> {
                        throw new IllegalStateException("boom");
                    });

            // then el error no se pierde en el hilo del pool: viaja en el futuro
            CompletionException ex = assertThrows(CompletionException.class, futuro::join);
            assertInstanceOf(IllegalStateException.class, ex.getCause());
        }
    }
}
