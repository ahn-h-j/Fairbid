FROM eclipse-temurin:17-jdk AS builder
WORKDIR /app

COPY gradlew .
COPY gradle gradle
RUN sed -i 's/\r$//' ./gradlew && chmod +x ./gradlew

COPY build.gradle settings.gradle ./
RUN ./gradlew dependencies --no-daemon

COPY src src
RUN ./gradlew bootJar --no-daemon

RUN java -Djarmode=layertools -jar build/libs/*.jar extract

FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=builder /app/dependencies/ ./
COPY --from=builder /app/spring-boot-loader/ ./
COPY --from=builder /app/snapshot-dependencies/ ./
COPY --from=builder /app/application/ ./

EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
