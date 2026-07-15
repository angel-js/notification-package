# Imagen exigida por el enunciado. Trae JDK 21 pero NO trae Maven: por eso se usa
# el Maven Wrapper (./mvnw), que se descarga Maven él solo dentro del contenedor.
FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app

# Copiar archivos del proyecto (.dockerignore excluye target/, .git, etc.)
COPY . .

# El wrapper necesita permiso de ejecución: git no siempre conserva el bit +x
RUN chmod +x mvnw

# Compilar la librería y bajar sus dependencias de runtime.
#
# copy-dependencies con includeScope=runtime deja en target/dependency/ solo lo que
# hace falta para EJECUTAR (slf4j-api y slf4j-simple). Quedan fuera Lombok (provided,
# solo compila) y JUnit/Mockito (test). Así el jar de la librería se mantiene limpio,
# sin empaquetar dependencias ajenas dentro: una librería no debe publicar un fat-jar,
# porque le impondría sus versiones a quien la consuma.
RUN ./mvnw clean package -DskipTests dependency:copy-dependencies -DincludeScope=runtime

# Ejecutar la clase de ejemplos con la librería + sus dependencias en el classpath
CMD ["java", "-cp", "target/notifications-lib-1.0.0.jar:target/dependency/*", \
     "com.notifications.examples.NotificationExamples"]
