package org.infinispan.test.fwk;

import static org.testng.Assert.assertEquals;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.infinispan.loaders.jdbc.DatabaseType;
import org.infinispan.loaders.jdbc.JdbcUtil;
import org.infinispan.loaders.jdbc.TableManipulation;
import org.infinispan.loaders.jdbc.TableName;
import org.infinispan.loaders.jdbc.configuration.AbstractJdbcCacheStoreConfigurationBuilder;
import org.infinispan.loaders.jdbc.configuration.ConnectionFactoryConfigurationBuilder;
import org.infinispan.loaders.jdbc.configuration.TableManipulationConfigurationBuilder;
import org.infinispan.loaders.jdbc.connectionfactory.ConnectionFactory;
import org.infinispan.loaders.jdbc.connectionfactory.ConnectionFactoryConfig;
import org.infinispan.loaders.jdbc.connectionfactory.PooledConnectionFactory;
import org.infinispan.loaders.jdbc.connectionfactory.SimpleConnectionFactory;

/**
 * Class that assures concurrent access to the in memory database.
 *
 * @author Mircea.Markus@jboss.com
 * @author Navin Surtani (<a href="mailto:nsurtani@redhat.com">nsurtani@redhat.com</a>)
 * @author Tristan Tarrant
 */

public class UnitTestDatabaseManager {
   private static final ConnectionFactoryConfig realConfig = new ConnectionFactoryConfig();

   private static AtomicInteger userIndex = new AtomicInteger(0);
   private static final String DB_TYPE = System.getProperty("infinispan.test.jdbc.db", "H2");
   private static final String H2_DRIVER = org.h2.Driver.class.getName();
   private static final String NON_EXISTENT_DRIVER = "non.existent.Driver";
   private static final DatabaseType dt;

   static {
      String driver = "";
      try {
         if (DB_TYPE.equalsIgnoreCase("mysql")) {
            driver = com.mysql.jdbc.Driver.class.getName();
            dt = DatabaseType.MYSQL;
         } else {
            driver = H2_DRIVER;
            dt = DatabaseType.H2;
         }
         try {
            Class.forName(driver);
         } catch (ClassNotFoundException e) {
            driver = H2_DRIVER;
            Class.forName(H2_DRIVER);
         }
      } catch (ClassNotFoundException e) {
         throw new RuntimeException(e);
      }
      realConfig.setDriverClass(driver);
      switch (dt) {
      case H2:
         realConfig.setConnectionUrl("jdbc:h2:mem:infinispan;DB_CLOSE_DELAY=-1");
         realConfig.setConnectionFactoryClass(PooledConnectionFactory.class.getName());
         realConfig.setUserName("sa");
         break;
      case MYSQL:
         realConfig.setConnectionUrl("jdbc:mysql://localhost/infinispan?user=ispn&password=ispn");
         realConfig.setConnectionFactoryClass(SimpleConnectionFactory.class.getName());
         realConfig.setUserName("ispn");
         realConfig.setPassword("ispn");
         break;
      }
   }

   public static ConnectionFactoryConfig getUniqueConnectionFactoryConfig() {
      synchronized (realConfig) {
         return returnBasedOnDifferentInstance();
      }
   }

   public static ConnectionFactoryConfigurationBuilder<?> configureUniqueConnectionFactory(AbstractJdbcCacheStoreConfigurationBuilder<?, ?> store) {
      switch (dt) {
      case H2:
         return store
            .connectionPool()
               .driverClass(org.h2.Driver.class)
               .connectionUrl(String.format("jdbc:h2:mem:%s;DB_CLOSE_DELAY=-1", extractTestName() + userIndex.incrementAndGet()))
               .username("sa");
      case MYSQL:
         return store
            .simpleConnection()
               .driverClass(com.mysql.jdbc.Driver.class)
               .connectionUrl("jdbc:mysql://localhost/infinispan?user=ispn&password=ispn")
               .username("ispn")
               .password("ispn");
      default:
         throw new RuntimeException("Cannot configure connection for database type "+dt);
      }
   }

   public static ConnectionFactoryConfigurationBuilder<?> configureBrokenConnectionFactory
         (AbstractJdbcCacheStoreConfigurationBuilder<?, ?> storeBuilder) {
      return storeBuilder.connectionPool()
                  .driverClass(NON_EXISTENT_DRIVER);
   }

   private static ConnectionFactoryConfig returnBasedOnDifferentInstance() {
      ConnectionFactoryConfig result = realConfig.clone();
      String jdbcUrl = result.getConnectionUrl();
      Pattern pattern = Pattern.compile("infinispan");
      Matcher matcher = pattern.matcher(jdbcUrl);
      boolean found = matcher.find();
      assert found : String.format("%1s not found in %2s", pattern, jdbcUrl);
      String newJdbcUrl = matcher.replaceFirst(extractTestName() + userIndex.incrementAndGet());
      result.setConnectionUrl(newJdbcUrl);
      return result;
   }

   private static String extractTestName() {
      StackTraceElement[] stack = Thread.currentThread().getStackTrace();
      if (stack.length == 0)
         return null;
      for (int i = stack.length - 1; i > 0; i--) {
         StackTraceElement e = stack[i];
         String className = e.getClassName();
         if (className.indexOf("org.infinispan") != -1)
            return className.replace('.', '_') + "_" + e.getMethodName();
      }
      return null;
   }


   public static void buildTableManipulation(TableManipulationConfigurationBuilder<?, ?> table, boolean binary) {
      table
         .databaseType(dt)
         .tableNamePrefix(binary ? "ISPN_BINARY" : "ISPN_STRING")
         .idColumnName("ID_COLUMN")
         .idColumnType(binary ? "INT" : "VARCHAR(255)")
         .dataColumnName("DATA_COLUMN")
         .dataColumnType("BLOB")
         .timestampColumnName("TIMESTAMP_COLUMN")
         .timestampColumnType("BIGINT");
   }

   /**
    * Counts the number of rows in the given table.
    */
   public static int rowCount(ConnectionFactory connectionFactory, TableName tableName) {

      Connection conn = null;
      PreparedStatement statement = null;
      ResultSet resultSet = null;
      try {
         conn = connectionFactory.getConnection();
         String sql = "SELECT count(*) FROM " + tableName;
         statement = conn.prepareStatement(sql);
         resultSet = statement.executeQuery();
         resultSet.next();
         return resultSet.getInt(1);
      } catch (Exception ex) {
         throw new RuntimeException(ex);
      } finally {
         JdbcUtil.safeClose(resultSet);
         JdbcUtil.safeClose(statement);
         connectionFactory.releaseConnection(conn);
      }
   }

   public static void verifyConnectionLeaks(ConnectionFactory connectionFactory) {
      if (connectionFactory instanceof PooledConnectionFactory) {
         PooledConnectionFactory pcf = (PooledConnectionFactory) connectionFactory;
         try {
            Thread.sleep(500); // C3P0 needs a little delay before reporting the correct number of connections. Bah!
            assertEquals(pcf.getPooledDataSource().getNumBusyConnectionsAllUsers(), 0);
         } catch (Exception e) {
            throw new RuntimeException(e);
         }
      } else if (connectionFactory instanceof SimpleConnectionFactory) {
         SimpleConnectionFactory scf = (SimpleConnectionFactory) connectionFactory;
         assertEquals(scf.getConnectionCount(), 0);
      }
   }

}
