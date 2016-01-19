/*
 * Copyright (C) 2015 Collaboratory
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.dockstore.common;

import java.sql.SQLException;
import java.util.Properties;
import javax.sql.DataSource;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.commons.dbcp2.ConnectionFactory;
import org.apache.commons.dbcp2.DriverManagerConnectionFactory;
import org.apache.commons.dbcp2.PoolableConnection;
import org.apache.commons.dbcp2.PoolableConnectionFactory;
import org.apache.commons.dbcp2.PoolingDataSource;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author xliu
 */
public class BasicPostgreSQL {

    protected static final Logger LOG = LoggerFactory.getLogger(BasicPostgreSQL.class);
    private static DataSource dataSource = null;

    public BasicPostgreSQL(HierarchicalINIConfiguration settings) {
        if (dataSource == null) {
            try {
                String nullConfigs = "";
                String host = settings.getString(Constants.POSTGRES_HOST);
                if (host == null) {
                    nullConfigs += "postgresHost ";
                }

                String user = settings.getString(Constants.POSTGRES_USERNAME);
                if (user == null) {
                    nullConfigs += "postgresUser ";
                }

                String pass = settings.getString(Constants.POSTGRES_PASSWORD);
                if (pass == null) {
                    nullConfigs += "postgresPass ";
                }

                String db = settings.getString(Constants.POSTGRES_DBNAME);
                if (db == null) {
                    nullConfigs += "postgresDBName ";
                }

                String maxConnections = settings.getString(Constants.POSTGRES_MAX_CONNECTIONS, "5");

                if (!nullConfigs.trim().isEmpty()) {
                    throw new NullPointerException("The following configuration values are null: " + nullConfigs
                            + ". Please check your configuration file.");
                }

                Class.forName("org.postgresql.Driver");

                String url = "jdbc:postgresql://" + host + "/" + db;
                LOG.debug("PostgreSQL URL is: " + url);
                Properties props = new Properties();
                props.setProperty("user", user);
                props.setProperty("password", pass);
                // props.setProperty("ssl","true");
                props.setProperty("initialSize", "5");
                props.setProperty("maxActive", maxConnections);

                ConnectionFactory connectionFactory = new DriverManagerConnectionFactory(url, props);
                PoolableConnectionFactory poolableConnectionFactory = new PoolableConnectionFactory(connectionFactory, null);
                poolableConnectionFactory.setValidationQuery("select count(*) from container;");
                ObjectPool<PoolableConnection> connectionPool = new GenericObjectPool<>(poolableConnectionFactory);
                poolableConnectionFactory.setPool(connectionPool);
                dataSource = new PoolingDataSource<>(connectionPool);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * This clears the data base for testing and creates an admin user
     */
    public void clearDatabase() {
        runUpdateStatement("delete from usercontainer;");
        runUpdateStatement("delete from endusergroup;");

        runUpdateStatement("delete from enduser;");
        runUpdateStatement("delete from token;");
        runUpdateStatement("delete from tagsourcefile;");
        runUpdateStatement("delete from sourcefile;");
        runUpdateStatement("delete from containertag;");
        runUpdateStatement("delete from tag;");
        runUpdateStatement("delete from container;");
        runUpdateStatement("delete from usergroup;");
    }

    protected <T> T runSelectStatement(String query, ResultSetHandler<T> handler, Object... params) {
        try {
            QueryRunner run = new QueryRunner(dataSource);
            return run.query(query, handler, params);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    protected <T> T runInsertStatement(String query, ResultSetHandler<T> handler, Object... params) {
        try {
            QueryRunner run = new QueryRunner(dataSource);
            return run.insert(query, handler, params);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    protected boolean runUpdateStatement(String query, Object... params) {
        try {
            QueryRunner run = new QueryRunner(dataSource);
            run.update(query, params);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    protected boolean runUpdateStatementConfidential(String query) {
        try {
            QueryRunner run = new QueryRunner(dataSource);
            run.update(query);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
