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
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
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
        Objects.requireNonNull(configuration, "configuration");
        HikariConfig hikari = new HikariConfig();
        MysqlDataSource mysql = new MysqlDataSource();
        mysql.setUrl(configuration.jdbcUrl());
        mysql.setUser(configuration.username());
        mysql.setPassword(configuration.password());
        hikari.setDataSource(injectReleaseFailure ? failReleaseLock(mysql) : mysql);
        hikari.setPoolName("valor-db37");
        hikari.setMaximumPoolSize(2);
        hikari.setMinimumIdle(0);
        hikari.setConnectionTimeout(5_000);
        hikari.setInitializationFailTimeout(-1);
        return new HikariDataSource(hikari);
    }

    public static HikariDataSource createVerifiedDataSource(Configuration configuration) throws SQLException {
        return createVerifiedDataSource(configuration, false);
    }

    public static HikariDataSource createVerifiedFailureInjectedDataSource(Configuration configuration) throws SQLException {
        return createVerifiedDataSource(configuration, true);
    }

    private static HikariDataSource createVerifiedDataSource(Configuration configuration, boolean injectReleaseFailure) throws SQLException {
        HikariDataSource dataSource = createDataSource(configuration, injectReleaseFailure);
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
        Objects.requireNonNull(delegate, "delegate");
        return proxy(DataSource.class, (proxy, method, args) -> {
            if (method.getName().equals("getConnection") && (args == null || args.length == 0)) {
                return proxyConnection((Connection) method.invoke(delegate, args));
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
            String authority = jdbcUrl.substring("jdbc:mysql://".length(), jdbcUrl.indexOf('/', "jdbc:mysql://".length()));
            if (authority.contains("@") || jdbcUrl.matches("(?i).*\\?(?:[^#]*&)?(?:user|password|passwordCharacterEncoding)=.*")) {
                throw new IllegalArgumentException("DB37 JDBC URL must not embed credentials");
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
        return proxy(Connection.class, (proxy, method, args) -> {
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
