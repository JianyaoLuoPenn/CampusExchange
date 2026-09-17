FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
RUN groupadd --system campus && useradd --system --gid campus campus
COPY --from=build /app/target/Ecommerce-multi-vendor-0.0.1-SNAPSHOT.jar /app/app.jar
USER campus
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar", "--debug=false"]
