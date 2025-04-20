# === СТАДИЯ 1: Сборка JAR файла ===
FROM maven:3.9.5-eclipse-temurin-17 AS builder

WORKDIR /app

# Копируем все исходники и зависимости
COPY pom.xml .
COPY src ./src

# Собираем проект (убираем тесты для скорости)
RUN mvn clean package -DskipTests

# === СТАДИЯ 2: Запуск приложения ===
FROM eclipse-temurin:17-jdk

WORKDIR /app

# Копируем собранный JAR
COPY --from=builder /app/target/*.jar app.jar

# Копируем application.properties
COPY src/main/resources/application.properties application.properties

ENTRYPOINT ["java", "-jar", "app.jar", "--spring.config.location=/app/application.properties"]
