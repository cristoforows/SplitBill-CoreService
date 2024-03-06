# Stage 1: Build stage
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /usr/src/app
COPY . .
RUN mvn package

# Stage 2: Final image
FROM amazoncorretto:21-alpine
WORKDIR /app
COPY --from=build /usr/src/app/target/SplitBillCoreService-1.0.0-SNAPSHOT-fat.jar myaapp.jar
EXPOSE 8888
CMD ["java", "-jar", "myaapp.jar"]
