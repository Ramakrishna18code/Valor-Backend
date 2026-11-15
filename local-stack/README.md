# VALOR local stack

Keep `Valor-Backend`, `Valor-Admin-Portal`, `Valor-Android-APP`, and `Valor-technician` as sibling directories. Install Java 21, Maven, Node/npm, and Docker Desktop. Copy `Valor-Backend/local-stack/.env.example` to `Valor-Backend/local-stack/.env` and replace the placeholder database passwords and JWT secret with local values; do not commit `.env`.

From PowerShell, run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\Valor-Backend\local-stack\start-local.ps1
```

The script verifies and packages the backend, packages the Spring Cloud Gateway, builds all three web apps with gateway URL `http://127.0.0.1:8080`, builds the backend image, starts MySQL and all five application containers, then runs `verify-local.ps1`. The verification checks app pages and JavaScript bundles, API health, browser preflights, an anonymous protected request, and a WebSocket handshake.

Local request path: Admin `:3000`, Customer `:3001`, and Technician `:3002` call Gateway `:8080` for `/api/**` and `/ws`; Gateway forwards to Backend `:8081`; Backend uses MySQL. Gateway forwards requests without changing authentication or job state rules. Port 8081 remains bound to loopback for direct development and health checks.

`docker compose -f Valor-Backend/local-stack/compose.yaml ps` shows the running services. The gateway health route is `http://127.0.0.1:8080/api/v1/health`.

If MySQL, Backend, and Gateway are already running locally but Docker Desktop is unavailable, the built web apps can be previewed without containers:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\Valor-Backend\local-stack\build-local-web.ps1
node .\Valor-Backend\local-stack\serve-local-web.cjs
```

The preview binds only to `127.0.0.1` and serves Admin on `:3000`, Customer on `:3001`, and Technician on `:3002`. It does not start MySQL, Backend, or Gateway. Stop it with Ctrl+C.
