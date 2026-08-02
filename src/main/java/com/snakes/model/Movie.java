package com.snakes.model;

import java.sql.*;
import java.util.ArrayList;
import java.lang.NullPointerException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import java.io.File;
import java.io.IOException;

public class Movie extends Media {
  
  static Connection con = null;
  private String _name = "null";
  private Integer _imdb = null;
  private boolean _snakes;
  private static final Logger logger = LogManager.getLogger("snakes");
  static JsonFactory factory = new JsonFactory();

  public Movie() {}
  public Movie(String name, Integer imdb, boolean snakes) {
     _name = name;
     _imdb = imdb;
     _snakes = snakes;
  }

  public void setName(String name) {
    this._name = name;
  }

  public void setImdb(Integer imdb) {
    this._imdb = imdb;
  }

  public void setSnakes(boolean snakes) {
    this._snakes = snakes;
  }

  public String getName() {
    return this._name;
  }

  public String getImdb() {
    /* Pad integer imdb # with 0s */
    String imdbpadded = String.format("%07d", _imdb);
    return imdbpadded;
  }

  public String getSnakes() {
    String snakes = null;
    if (_snakes) {
      snakes = "Snakes";
    }
    else {
      snakes = "No Snakes";
    }
    return snakes;
  }

  public String getSnakesBool() {
    String snakes = null;
    if (_snakes) {
      snakes = "true";
    }
    else {
      snakes = "false";
    }
    return snakes;
  }

  public static Movie[] getMovies() {

    ArrayList<Movie> movies = new ArrayList<Movie>();

    try {
      con = getConnection();
      // If that fails, send dummy entries
      if (con == null) {
        logger.warn("Connection Failed!");
        Movie failed = new Movie("Connection Failed", 99999999, false);
        return new Movie[] { failed };
      }
      Statement stmt = con.createStatement();
      String sql = "SELECT * FROM Movies;";
      ResultSet rs = stmt.executeQuery(sql);

      while(rs.next()){
        // Retrieve by column name
        String name = rs.getString("name");
        Integer imdb = rs.getInt("imdb");
        boolean snakes = rs.getBoolean("snakes");
        Movie movie = new Movie(name, imdb, snakes);
        movies.add(movie);
      }
    }
    catch (SQLException e) { logger.warn(e.toString());}

    return movies.toArray(new Movie[movies.size()]);
  }

  public static Movie[] getMovies(String title) {

    ArrayList<Movie> movies = new ArrayList<Movie>();

    try {
      con = getConnection();
      // If that fails, send dummy entry
      if (con == null) {
        logger.warn("Connection Failed!");
        Movie failed = new Movie("Connection Failed", 99999999, false);
        return new Movie[] { failed };
      }
      Statement stmt = con.createStatement();
      String sql = null;
      if (title.matches(".*[^a-zA-Z0-9_\\s].*"))
        return new Movie[0];
      else
        sql = "SELECT * FROM Movies WHERE UPPER(name) LIKE UPPER('"+title+"');";
      ResultSet rs = stmt.executeQuery(sql);

      while(rs.next()){
        // Retrieve by column name
        String name = rs.getString("name");
        Integer imdb = rs.getInt("imdb");
        boolean snakes = rs.getBoolean("snakes");
        Movie movie = new Movie(name, imdb, snakes);
        movies.add(movie);
      }
    }
    catch (SQLException e) { logger.warn(e.toString());}

    return movies.toArray(new Movie[movies.size()]);
  }

  public static void addMovie(String name, Integer imdb, boolean snakes) {
    con = getConnection();
    // If the connection is null, give up
    if (con == null) {
      return;
    }
    try {
      Statement create = con.createStatement();
      String insertRow1 = "INSERT INTO Movies (Name, IMDB, Snakes) VALUES ('"+name+"', '"+imdb+"', "+snakes+");";
      logger.trace("adding movie with statement: "+ insertRow1);
      create.addBatch(insertRow1);
      create.executeBatch();
      create.close();
    }
    catch (SQLException f) { logger.warn(f.toString());}
  }

  private static void initDatabase() {
    // Retrieve the connection (it should already exist)
    con = getConnection();
    // If the connection is null, give up
    if (con == null) {
      return;
    }
    // Attempt to read movies table
    try {
      Statement stmt = con.createStatement();
      String sql = "SELECT * FROM Movies;";
      ResultSet rs = stmt.executeQuery(sql);
    }
    // If the movies table doesn't exist, create it
    catch (SQLException e) {
      try {
        logger.warn("Initializing Database");
        // Create table for MySQL
        logger.info("Creating table");
        String createTable = "CREATE TABLE IF NOT EXISTS Movies (Name VARCHAR(255), IMDB INT, Snakes BOOLEAN);";
        Statement createStmt = con.createStatement();
        createStmt.execute(createTable);
      }
      catch (SQLException f) { logger.warn(f.toString());}

      try {
        // Read seed file if present
        logger.info("reading seed file");
        File databaseSeed = new File("/tmp/database-seed.json");
        if (databaseSeed.exists()) {
          ObjectMapper mapper = new ObjectMapper();
          Movie[] movies = mapper.readValue(databaseSeed, Movie[].class);
          Statement create = con.createStatement();
          logger.info("adding movies to batch");
          for (Movie movie : movies) {
            String row = "INSERT INTO Movies (Name, IMDB, Snakes) VALUES ('" + movie.getName() + "', '" + movie.getImdb() + "', " + movie.getSnakesBool() + ");";
            logger.info("- "+row);  
            create.addBatch(row);
          }
          create.executeBatch();
          create.close();
          logger.warn("Initialized Database");
        }
      }
      catch (IOException g) { logger.warn(g.toString());}
      catch (SQLException h) { logger.warn(h.toString());}
    }
  }

  private static Connection getConnection() {
    // Return existing connection if active
    try {
      if (con != null && !con.isClosed()) {
        return con;
      }
    } catch (SQLException e) {
      logger.warn(e.toString());
    }

    logger.trace("Getting database connection...");
    con = getRemoteConnection();
    
    if (con == null) {
      con = getLocalConnection();
    }

    if (con == null) {
      return null;
    }

    initDatabase();
    return con;
  }

  private static Connection getRemoteConnection() {
    try {
      Class.forName("com.mysql.cj.jdbc.Driver");

      // Read values from system environment or system properties
      String hostname = getEnv("MYSQL_HOST", getEnv("RDS_HOSTNAME", null));
      String dbName   = getEnv("MYSQL_DATABASE", getEnv("RDS_DB_NAME", "ebdb"));
      String userName = getEnv("MYSQL_USER", getEnv("RDS_USERNAME", null));
      String password = getEnv("MYSQL_PASSWORD", getEnv("RDS_PASSWORD", null));
      String port     = getEnv("MYSQL_PORT", getEnv("RDS_PORT", "3306"));

      if (hostname != null && userName != null && password != null) {
        String jdbcUrl = "jdbc:mysql://" + hostname + ":" + port + "/" + dbName + 
                         "?useSSL=true&requireSSL=false&serverTimezone=UTC";
        logger.trace("Connecting to MySQL at: " + hostname);
        Connection con = DriverManager.getConnection(jdbcUrl, userName, password);
        logger.info("MySQL Remote connection successful.");
        return con;
      }
    }
    catch (ClassNotFoundException e) { logger.warn("MySQL Driver not found: " + e.toString()); }
    catch (SQLException e) { logger.warn("MySQL Connection error: " + e.toString()); }

    return null;
  }

  private static Connection getLocalConnection() {
    try {
      Class.forName("com.mysql.cj.jdbc.Driver");
      logger.info("Getting local MySQL connection");
      Connection con = DriverManager.getConnection(
            "jdbc:mysql://localhost:3306/snakes?useSSL=false&serverTimezone=UTC",
            "root",
            "root");
      logger.info("Local MySQL connection successful.");
      return con;
    }
    catch (ClassNotFoundException e) { logger.warn(e.toString()); }
    catch (SQLException e) { logger.warn(e.toString()); }
    return null;
  }

  private static String getEnv(String name, String defaultValue) {
    String val = System.getenv(name);
    if (val == null) {
      val = System.getProperty(name);
    }
    return (val != null) ? val : defaultValue;
  }
}
