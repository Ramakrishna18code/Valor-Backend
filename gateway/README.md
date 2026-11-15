# VALOR local API gateway

This Spring Cloud Gateway service listens on port 8080 and forwards `/api/**` HTTP requests and `/ws` WebSocket connections to the existing backend on port 8081. It does not replace backend JWT, role, OTP, or job state checks.

Build with Java 21 and Maven:

```powershell
mvn -f gateway/pom.xml clean package
```

For a standalone local process, run the packaged JAR with the backend already running on port 8081. In Docker Compose, set `BACKEND_URL=http://api:8081` and `BACKEND_WS_URL=ws://api:8081`. The repeatable local stack and verification command are documented in `../local-stack/README.md`.
