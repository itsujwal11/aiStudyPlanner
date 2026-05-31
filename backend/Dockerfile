
FROM eclipse-temurin:17-jdk-jammy

WORKDIR /app

COPY pom.xml .
RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*
RUN mvn dependency:go-offline

COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jdk-jammy
WORKDIR /app
COPY --from=0 /app/target/*.jar app.jar

EXPOSE 9096

ENTRYPOINT ["java", "-jar", "app.jar"]
