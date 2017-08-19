/*
 * Copyright (c) 2017. iDoc LLC
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *     (1) Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *     (2) Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in
 *     the documentation and/or other materials provided with the
 *     distribution.
 *
 *     (3)The name of the author may not be used to
 *     endorse or promote products derived from this software without
 *     specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
 * IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package ua.papka24.server.db.dao;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import ua.papka24.server.Main;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;


public abstract class DAO {
    protected static final Logger log = LoggerFactory.getLogger("DB");
    private static HikariDataSource connectionPool;

    public static long NULL_ID = -1;

    static {
        boolean stop = false;
        for(int i=0; i < 3 & !stop; i++) {
            try {
                Class.forName(Main.property.getProperty("jdbc.driver", "org.postgresql.Driver"));
                HikariConfig config = new HikariConfig();
                config.setJdbcUrl(Main.property.getProperty("jdbc.url") + Main.property.getProperty("jdbc.database"));
                config.setUsername(Main.property.getProperty("jdbc.username"));
                config.setPassword(Main.property.getProperty("jdbc.password"));
                config.addDataSourceProperty("cachePrepStmts", "true");
                config.addDataSourceProperty("prepStmtCacheSize", "250");
                config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
                config.addDataSourceProperty("leakDetectionThreshold", "4000");
                config.addDataSourceProperty("assumeMinServerVersion", "9.0");
                config.setAutoCommit(false);
                config.setMaximumPoolSize(Integer.valueOf(Main.property.getProperty("jdbc.maxPoolSize", "10")));
                connectionPool = new HikariDataSource(config);
                stop = true;
            } catch (Exception e) {
                log.error("error creating sql pool:", e.getMessage());
                try {
                    TimeUnit.SECONDS.sleep(1 + (i * 10));
                } catch (InterruptedException ignored) {}
            }
        }
    }

    protected Connection getConnection() {
        try {
          return connectionPool.getConnection();
        } catch (SQLException e){
            log.error("Error get connection", e.getMessage());
        }
        return null;
    }

    public static boolean isActive() {
        return connectionPool != null && !connectionPool.isClosed();
    }
}
