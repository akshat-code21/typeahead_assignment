FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn

RUN ./mvnw dependency:go-offline -q

COPY src ./src

RUN ./mvnw package -DskipTests -q


FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder /app/target/typeahead-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

CMD [ "java","-jar","app.jar" ]

