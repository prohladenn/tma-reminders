FROM maven:3.9.6-eclipse-temurin-17 AS build

WORKDIR /app

COPY pom.xml .
RUN mvn -B -q -DskipTests dependency:go-offline

COPY src ./src

RUN mvn -B -Pproduction -DskipTests package

FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["java","-jar","/app/app.jar"]
