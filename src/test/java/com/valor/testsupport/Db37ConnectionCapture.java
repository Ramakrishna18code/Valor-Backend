package com.valor.testsupport;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class Db37ConnectionCapture {
    private final AtomicReference<Connection> connection = new AtomicReference<>();
    private final AtomicReference<Long> connectionId = new AtomicReference<>();
    private final AtomicReference<Connection> evictedConnection = new AtomicReference<>();
    private final AtomicReference<Long> evictedConnectionId = new AtomicReference<>();
    private final AtomicBoolean duplicateEvictionObservation = new AtomicBoolean();

    public void observe(Connection observed) {
        connection.compareAndSet(null, observed);
        connectionId.compareAndSet(null, readConnectionId(observed));
    }

    public Connection connection() {
        return connection.get();
    }

    public long originalConnectionId() {
        Long id = connectionId.get();
        if (id == null) throw new AssertionError("DB37 lock connection was not captured");
        return id;
    }

    public void observeEviction(Connection evicted) {
        if (!evictedConnection.compareAndSet(null, evicted)) {
            duplicateEvictionObservation.set(true);
            return;
        }
        evictedConnectionId.set(readConnectionId(evicted));
    }

    public Connection evictedConnection() {
        return evictedConnection.get();
    }

    public long evictedConnectionId() {
        Long id = evictedConnectionId.get();
        if (id == null) throw new AssertionError("DB37 eviction connection was not captured");
        return id;
    }

    public boolean hasDuplicateEvictionObservation() {
        return duplicateEvictionObservation.get();
    }

    private static long readConnectionId(Connection connection) {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("select connection_id()")) {
            if (!result.next()) throw new SQLException("DB37 CONNECTION_ID() returned no row");
            return result.getLong(1);
        } catch (SQLException failure) {
            throw new IllegalStateException("DB37 could not capture CONNECTION_ID()", failure);
        }
    }
}
