FROM maven:3.9-eclipse-temurin-21-noble@sha256:99e61abcff91a9b1333463bd8451fb18495d6eba9250ac66a338b518f8278320 AS build

WORKDIR /workspace

COPY pom.xml .
COPY src ./src

# The complete test suite runs in Maven verification/CI. Keeping test compilation
# here catches source/type errors without duplicating resource-heavy Spring tests.
RUN mvn -B package -DskipTests

FROM eclipse-temurin:21.0.13_9-jre-noble@sha256:0d57a8e90c2bb844c5a8e1b2b71e11eff06dad7edcfd803cd21ed4ae203b2515
ENV JAVA_TOOL_OPTIONS="-XX:+UnlockDiagnosticVMOptions -XX:-UseBASE64Intrinsics"
WORKDIR /app
COPY --from=build /workspace/target/valor-backend-0.0.1-SNAPSHOT.jar /app/app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
