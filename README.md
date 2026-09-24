# sprintmodus-common-lib

Shared library for the Sprintmodus backend services (auth-service, project-service, workitem-service). A plain jar: no
application class, and it is **not component-scanned**. A service pulls in what it needs with `@Import`.

## What is in it

| Package | Contents |
|---|---|
| `result` | `Result<T, E>` (sealed: `Success` / `Failure`, with `map`, `flatMap`, `tap`, `tapError`, `mapError`, `fold`), `Unit`, `ApplicationError`. Use cases return `Result`; infrastructure failures stay exceptions |
| `tenant` | `TenantContext` (per-thread tenant + subscription limits, `callAs` scopes it), `TenantDatabaseNameResolver` (`tenant_{tenantId without dashes}`), `TenantRoutingDataSource` (one lazily created Hikari pool per tenant database) and `TenantDataSourceConfiguration` |
| `security` | `JwtTokenVerifier`, `TenantSecurityFilter`, `TenantMembershipVerifier` and `TenantSecurityConfiguration` (a ready `SecurityFilterChain`) |
| `web` | `ErrorResponse` (`{code, message, timestamp}`) and `GlobalExceptionHandler` via `CommonWebConfiguration` |

## Using it

```java
@Configuration
@Import({ TenantSecurityConfiguration.class, CommonWebConfiguration.class })   // brings TenantDataSourceConfiguration too
class TenancyConfig { }
```

```properties
spring.datasource.tenant.host=localhost          # + port, username, password, max-pool-size, jdbc-parameters
sprintmodus.jwt.secret=${JWT_SECRET:}            # same secret auth-service signs with, at least 64 bytes
sprintmodus.jwt.issuer=sprintmodus-auth
```

`TenantSecurityConfiguration` secures `/api/**` (everything else is closed, CORS is left to the gateway) and installs
`TenantSecurityFilter`, which for each request:

1. verifies the bearer JWT (HS512 signature, issuer, expiry), else answers **401** (`INVALID_TOKEN`, `TOKEN_EXPIRED`);
2. checks the user is an **active member of the tenant named in the token** by querying that tenant's `User` table, so a
   deactivated user loses access at once (401), and a tenant database that cannot be reached is a generic 500;
3. sets `TenantContext` (which selects the tenant database) and the Spring `SecurityContext`;
4. clears both in a `finally`.

A request without a bearer token continues unauthenticated and the authorization rules answer 401 `UNAUTHENTICATED`.
The token is kept as the authentication's credentials so services can relay it on outgoing calls.

Services build their JPA `EntityManagerFactory` on this DataSource (with the dialect set explicitly, since there is no database to inspect at startup). The tenant must be in the context *before* a statement or transaction starts, because that is when the database is chosen; `TenantContext.callAs(...)` scopes it.

`TenantRoutingDataSource` never falls back to another database: with no tenant in the context it fails. A pool that
cannot be opened (for example the database does not exist) is reported as an `SQLException` and not cached, so the next
request retries. Pools are never evicted; see the auth-service README.

## The token contract

Claims: `sub` (user code), `email`, `tenantId`, `organizationCode`, `role`, `plan`, `maxProjects`, `maxUsers`,
`maxStorageMB`, `iat`, `exp`, `iss`. There is deliberately no database host or name. auth-service issues the tokens;
`TokenContractTest` in its repository checks they are accepted by this library's verifier.

## Tests

```bash
./mvnw clean install
```
