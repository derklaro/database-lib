/*
 * MIT License
 *
 * Copyright (c) Pasqual Koschmieder
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package de.derklaro.database.mysql.connection;

import de.derklaro.database.api.DatabaseProvider;
import de.derklaro.database.api.connection.ConnectionConfiguration;
import de.derklaro.database.api.connection.ConnectionProvider;
import de.derklaro.database.mysql.MySQLDatabaseProvider;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

public class MySQLConnectionProvider implements ConnectionProvider {

    private static final String CONNECT_URL = "jdbc:mysql://%s:%d/%s?serverTimezone=UTC&useSSL=%b&trustServerCertificate=%b";

    private final Collection<DatabaseProvider> providers = new CopyOnWriteArrayList<>();

    @Override
    public @NotNull CompletableFuture<Optional<DatabaseProvider>> connect(@NotNull ConnectionConfiguration connectionConfiguration) {
        if (!connectionConfiguration.isLoaded()) {
            throw new RuntimeException("Can only connect to a database using a loaded connection configuration");
        }

        return CompletableFuture.supplyAsync(() -> {
            HikariConfig hikariConfig = new HikariConfig();

            hikariConfig.setJdbcUrl(String.format(
                    CONNECT_URL,
                    connectionConfiguration.getHost(),
                    connectionConfiguration.getPort(),
                    connectionConfiguration.getTargetDatabase(),
                    connectionConfiguration.useSSL(),
                    connectionConfiguration.useSSL()
            ));
            hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
            hikariConfig.setUsername(connectionConfiguration.getUserName());
            hikariConfig.setPassword(connectionConfiguration.getPassword());

            hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
            hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
            hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
            hikariConfig.addDataSourceProperty("useLocalSessionState", "true");
            hikariConfig.addDataSourceProperty("rewriteBatchedStatements", "true");
            hikariConfig.addDataSourceProperty("cacheResultSetMetadata", "true");
            hikariConfig.addDataSourceProperty("cacheServerConfiguration", "true");
            hikariConfig.addDataSourceProperty("elideSetAutoCommits", "true");
            hikariConfig.addDataSourceProperty("maintainTimeStats", "false");

            DatabaseProvider result = new MySQLDatabaseProvider(new HikariDataSource(hikariConfig));
            this.providers.add(result);
            return Optional.of(result);
        });
    }

    @Override
    public @NotNull CompletableFuture<Void> closeAllConnections() {
        return CompletableFuture.supplyAsync(() -> {
            for (DatabaseProvider provider : this.providers) {
                provider.closeConnection().join();
            }

            this.providers.clear();
            return null;
        });
    }
}