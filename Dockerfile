#
# Build stage
#
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

#
# Package stage
#
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/StockManagement-0.0.1-SNAPSHOT.jar StockManagement.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","StockManagement.jar"]
