package com.valor.testsupport;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.mysql.cj.jdbc.MysqlDataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import java.util.function.Consumer;
import javax.sql.DataSource;

public final class Db37MysqlTestHarness {
    private static final String URL_VARIABLE = "DB37_MYSQL_URL";
    private static final String USERNAME_VARIABLE = "DB37_MYSQL_USERNAME";
    private static final String PASSWORD_VARIABLE = "DB37_MYSQL_PASSWORD";

    private Db37MysqlTestHarness() {
    }

    public static Configuration fromEnvironment() {
        return new Configuration(required(URL_VARIABLE), required(USERNAME_VARIABLE), required(PASSWORD_VARIABLE));
    }

    public static Optional<Configuration> fromEnvironmentIfOptedIn() {
        String url = System.getenv(URL_VARIABLE);
        String username = System.getenv(USERNAME_VARIABLE);
        String password = System.getenv(PASSWORD_VARIABLE);
        if (url == null && username == null && password == null) return Optional.empty();
        return Optional.of(new Configuration(requiredValue(URL_VARIABLE, url), requiredValue(USERNAME_VARIABLE, username), requiredValue(PASSWORD_VARIABLE, password)));
    }

    public static HikariDataSource createDataSource(Configuration configuration) {
        return createDataSource(configuration, false);
    }

    public static HikariDataSource createFailureInjectedDataSource(Configuration configuration) {
        return createDataSource(configuration, true);
    }

    private static HikariDataSource createDataSource(Configuration configuration, boolean injectReleaseFailure) {
        return createDataSource(configuration, injectReleaseFailure, connection -> { });
    }

    private static HikariDataSource createDataSource(Configuration configuration, boolean injectReleaseFailure,
            Consumer<Connection> connectionObserver) {
        return createDataSource(configuration, injectReleaseFailure, connectionObserver, connection -> { });
    }

    private static HikariDataSource createDataSource(Configuration configuration, boolean injectReleaseFailure,
            Consumer<Connection> connectionObserver, Consumer<Connection> evictionObserver) {
        Objects.requireNonNull(configuration, "configuration");
        HikariConfig hikari = new HikariConfig();
        MysqlDataSource mysql = new MysqlDataSource();
        mysql.setUrl(configuration.jdbcUrl());
        mysql.setUser(configuration.username());
        mysql.setPassword(configuration.password());
        hikari.setDataSource(injectReleaseFailure ? failReleaseLock(mysql, connectionObserver) : mysql);
        hikari.setPoolName("valor-db37");
        hikari.setMaximumPoolSize(2);
        hikari.setMinimumIdle(0);
        hikari.setConnectionTimeout(5_000);
        hikari.setInitializationFailTimeout(-1);
        return new ObservingHikariDataSource(hikari, evictionObserver);
    }

    public static HikariDataSource createVerifiedDataSource(Configuration configuration) throws SQLException {
        return createVerifiedDataSource(configuration, false);
    }

    public static HikariDataSource createVerifiedFailureInjectedDataSource(Configuration configuration) throws SQLException {
        return createVerifiedDataSource(configuration, true);
    }

    public static HikariDataSource createVerifiedFailureInjectedDataSource(
            Configuration configuration, Consumer<Connection> connectionObserver) throws SQLException {
        return createVerifiedDataSource(configuration, true, connectionObserver, connection -> { });
    }

    public static HikariDataSource createVerifiedFailureInjectedDataSource(Configuration configuration,
            Consumer<Connection> connectionObserver, Consumer<Connection> evictionObserver) throws SQLException {
        return createVerifiedDataSource(configuration, true, connectionObserver, evictionObserver);
    }

    private static HikariDataSource createVerifiedDataSource(Configuration configuration, boolean injectReleaseFailure) throws SQLException {
        return createVerifiedDataSource(configuration, injectReleaseFailure, connection -> { });
    }

    private static HikariDataSource createVerifiedDataSource(Configuration configuration,
            boolean injectReleaseFailure, Consumer<Connection> connectionObserver) throws SQLException {
        return createVerifiedDataSource(configuration, injectReleaseFailure, connectionObserver, connection -> { });
    }

    private static HikariDataSource createVerifiedDataSource(Configuration configuration,
            boolean injectReleaseFailure, Consumer<Connection> connectionObserver,
            Consumer<Connection> evictionObserver) throws SQLException {
        HikariDataSource dataSource = createDataSource(configuration, injectReleaseFailure,
                connectionObserver, evictionObserver);
        try {
            verifyConnectedSchema(dataSource, configuration);
            return dataSource;
        } catch (SQLException | RuntimeException failure) {
            dataSource.close();
            throw failure;
        }
    }

    public static void verifyConnectedSchema(DataSource dataSource, Configuration configuration) throws SQLException {
        Objects.requireNonNull(dataSource, "dataSource");
        Objects.requireNonNull(configuration, "configuration");
        try (Connection connection = dataSource.getConnection();
             PreparedStatement database = connection.prepareStatement("select database()");
             ResultSet result = database.executeQuery()) {
            if (!result.next() || !configuration.schema().equals(result.getString(1))) {
                throw new SQLException("DB37 connected schema does not match the approved disposable schema");
            }
        }
    }

    public static DataSource failReleaseLock(DataSource delegate) {
        return failReleaseLock(delegate, connection -> { });
    }

    public static DataSource failReleaseLock(DataSource delegate, Consumer<Connection> connectionObserver) {
        Objects.requireNonNull(delegate, "delegate");
        return proxy(DataSource.class, (proxy, method, args) -> {
            if (method.getName().equals("getConnection") && (args == null || args.length == 0)) {
                return proxyConnection((Connection) method.invoke(delegate, args), connectionObserver);
            }
            return invoke(delegate, method, args);
        });
    }

    public record Configuration(String jdbcUrl, String username, String password) {
        public Configuration {
            validate(jdbcUrl, username, password);
        }

        public String schema() {
            String withoutParameters = jdbcUrl.substring(0, jdbcUrl.indexOf('?'));
            return withoutParameters.substring(withoutParameters.lastIndexOf('/') + 1);
        }

        private static void validate(String jdbcUrl, String username, String password) {
            if (blank(jdbcUrl) || blank(username) || blank(password)) {
                throw new IllegalArgumentException("DB37 MySQL configuration is incomplete");
            }
            if (!jdbcUrl.startsWith("jdbc:mysql://") || !jdbcUrl.contains("?")) {
                throw new IllegalArgumentException("DB37 requires a parameterized MySQL JDBC URL");
            }
            URI endpoint = parseLocalEndpoint(jdbcUrl);
            String authority = endpoint.getRawAuthority();
            if (authority == null || authority.contains("@") || authority.contains(",")
                    || !"localhost".equalsIgnoreCase(endpoint.getHost()) || endpoint.getPort() != 3306) {
                throw new IllegalArgumentException("DB37 requires the localhost:3306 endpoint");
            }
            validateQuery(endpoint.getRawQuery());
            if (!"valor_backend_db37_gopi01".equals(new ConfigurationUnchecked(jdbcUrl).schema())) {
                throw new IllegalArgumentException("DB37 requires the valor_backend_db37_gopi01 schema");
            }
            String schema = new ConfigurationUnchecked(jdbcUrl).schema();
            if (!schema.matches("valor_backend_db37_[a-z0-9]+")) {
                throw new IllegalArgumentException("DB37 requires a disposable valor_backend_db37_<suffix> schema");
            }
            String lower = schema.toLowerCase(Locale.ROOT);
            if (lower.equals("valor_world_dev") || lower.contains("prod") || lower.contains("shared") || lower.contains("audit")) {
                throw new IllegalArgumentException("DB37 schema is not an approved disposable schema");
            }
        }

        private static URI parseLocalEndpoint(String jdbcUrl) {
            try {
                URI endpoint = new URI(jdbcUrl.substring("jdbc:".length()));
                if (!"mysql".equalsIgnoreCase(endpoint.getScheme()) || endpoint.getPath() == null
                        || endpoint.getPath().length() < 2 || endpoint.getPath().indexOf('/', 1) >= 0
                        || endpoint.getRawFragment() != null) {
                    throw new IllegalArgumentException("DB37 requires a single local MySQL endpoint");
                }
                return endpoint;
            } catch (URISyntaxException | IllegalArgumentException failure) {
                throw new IllegalArgumentException("DB37 requires a valid local MySQL JDBC URL", failure);
            }
        }

        private static void validateQuery(String rawQuery) {
            if (rawQuery == null || rawQuery.isEmpty()) {
                throw new IllegalArgumentException("DB37 requires explicit JDBC query parameters");
            }
            Set<String> names = new HashSet<>();
            String[] pairs = rawQuery.split("&", -1);
            for (String pair : pairs) {
                int equals = pair.indexOf('=');
                if (equals <= 0 || equals != pair.lastIndexOf('=')) {
                    throw new IllegalArgumentException("DB37 JDBC query contains a malformed parameter");
                }
                String name = decodeQueryComponent(pair.substring(0, equals));
                String value = decodeQueryComponent(pair.substring(equals + 1));
                String normalizedName = name.toLowerCase(Locale.ROOT);
                if (!names.add(normalizedName)) {
                    throw new IllegalArgumentException("DB37 JDBC query contains a duplicate parameter");
                }
                if (normalizedName.equals("usessl") && value.equals("false")) continue;
                if (normalizedName.equals("servertimezone") && value.equals("UTC")) continue;
                throw new IllegalArgumentException("DB37 JDBC query contains an unapproved parameter");
            }
        }

        private static String decodeQueryComponent(String raw) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(raw.length());
            for (int i = 0; i < raw.length(); i++) {
                char current = raw.charAt(i);
                if (current == '%') {
                    if (i + 2 >= raw.length()) throw new IllegalArgumentException("DB37 JDBC query has malformed encoding");
                    int high = Character.digit(raw.charAt(++i), 16);
                    int low = Character.digit(raw.charAt(++i), 16);
                    if (high < 0 || low < 0) throw new IllegalArgumentException("DB37 JDBC query has malformed encoding");
                    bytes.write((high << 4) + low);
                } else {
                    if (current == '+' || current > 0x7f) {
                        throw new IllegalArgumentException("DB37 JDBC query must use strict percent encoding");
                    }
                    bytes.write(current);
                }
            }
            try {
                CharBuffer decoded = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes.toByteArray()));
                return decoded.toString();
            } catch (CharacterCodingException failure) {
                throw new IllegalArgumentException("DB37 JDBC query has invalid UTF-8", failure);
            }
        }

        private static boolean blank(String value) {
            return value == null || value.isBlank();
        }
    }

    private record ConfigurationUnchecked(String jdbcUrl) {
        private String schema() {
            String withoutParameters = jdbcUrl.substring(0, jdbcUrl.indexOf('?'));
            return withoutParameters.substring(withoutParameters.lastIndexOf('/') + 1);
        }
    }

    private static String required(String name) {
        return requiredValue(name, System.getenv(name));
    }

    private static String requiredValue(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be explicitly supplied for DB37");
        }
        return value;
    }

    private static Connection proxyConnection(Connection delegate) {
        return proxyConnection(delegate, connection -> { });
    }

    private static Connection proxyConnection(Connection delegate, Consumer<Connection> connectionObserver) {
        return proxy(Connection.class, (proxy, method, args) -> {
            if (method.getName().equals("prepareStatement") && args != null && args.length > 0
                    && isGetLockSql(args[0])) {
                connectionObserver.accept(delegate);
            }
            if (method.getName().equals("prepareStatement") && args != null && args.length > 0
                    && isReleaseLockSql(args[0])) {
                return proxyPreparedStatement((PreparedStatement) invoke(delegate, method, args));
            }
            return invoke(delegate, method, args);
        });
    }

    private static PreparedStatement proxyPreparedStatement(PreparedStatement delegate) {
        return proxy(PreparedStatement.class, (proxy, method, args) -> {
            if (method.getName().equals("executeQuery") && (args == null || args.length == 0)) {
                throw new SQLException("DB37 injected RELEASE_LOCK failure");
            }
            return invoke(delegate, method, args);
        });
    }

    private static boolean isReleaseLockSql(Object sql) {
        return sql instanceof String normalized
                && normalized.replaceAll("\\s+", "").toLowerCase(Locale.ROOT).equals("selectrelease_lock(?)");
    }

    private static boolean isGetLockSql(Object sql) {
        return sql instanceof String normalized
                && normalized.replaceAll("\\s+", "").toLowerCase(Locale.ROOT).equals("selectget_lock(?,10)");
    }

    private static final class ObservingHikariDataSource extends HikariDataSource {
        private final Consumer<Connection> evictionObserver;

        private ObservingHikariDataSource(HikariConfig configuration, Consumer<Connection> evictionObserver) {
            super(configuration);
            this.evictionObserver = evictionObserver;
        }

        @Override
        public void evictConnection(Connection connection) {
            try {
                evictionObserver.accept(connection);
            } finally {
                super.evictConnection(connection);
            }
        }
    }

    private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (java.lang.reflect.InvocationTargetException exception) {
            throw exception.getCause();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }
}
