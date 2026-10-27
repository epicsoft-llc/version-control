package one.epicsoft.vc.adapter;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import one.epicsoft.vc.config.VulnerabilityProperties;
import one.epicsoft.vc.data.CpeRange;
import one.epicsoft.vc.data.DepcheckLookup;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.nonNull;
import static one.epicsoft.vc.util.VcUtil.isConfigured;
import static one.epicsoft.vc.util.VcUtil.nonBlank;

/** Read only. The bounds are text, so CpeVersion decides which entry meets a version; unquoted identifiers serve every engine. */
@Slf4j
@Component
@RequiredArgsConstructor
public class DependencyCheckAdapter {

  /** Long enough for a product with a few thousand entries, short enough not to hold the scheduler. */
  private static final int QUERY_TIMEOUT_SECONDS = 30;

  private static final int SUMMARY_LENGTH = 300;

  private static final String RANGES = """
      SELECT v.cve, v.description, v.v2Score, v.v2Severity, v.v3BaseScore, v.v3BaseSeverity, v.v4baseScore,
             v.v4baseSeverity, c.version, s.versionStartIncluding, s.versionStartExcluding, s.versionEndIncluding,
             s.versionEndExcluding, s.vulnerable, k.cveID AS exploitedCve, k.vulnerabilityName
      FROM cpeEntry c
      JOIN software s ON s.cpeEntryId = c.id
      JOIN vulnerability v ON v.id = s.cveid
      LEFT JOIN knownExploited k ON k.cveID = v.cve
      WHERE c.part = 'a' AND c.vendor = ? AND c.product = ?
      """;

  // Not by name: VALUE is a keyword in H2.
  private static final String DATA_AS_OF = "SELECT * FROM properties WHERE id = 'nvd.api.last.modified'";

  private final VulnerabilityProperties vulnerabilityProperties;

  /** One per driver JAR for the life of the application; a loader per lookup would never be freed. */
  private final Map<String, URLClassLoader> driverLoaders = new ConcurrentHashMap<>();

  @Nonnull
  public DepcheckLookup lookUp(@Nonnull final String vendor, @Nonnull final String product) {
    try (Connection connection = this.connect()) {
      connection.setReadOnly(true);
      return new DepcheckLookup(ranges(connection, vendor, product), dataAsOf(connection));
    } catch (final SQLException exception) {
      // The class and the state only: a driver's message can carry the URL, and the URL can carry a password.
      log.warn("Dependency-Check database lookup failed: {} (SQL state {})", exception.getClass().getSimpleName(),
               exception.getSQLState());
      throw new SourceException("The Dependency-Check database could not be read (SQL state %s)".formatted(exception.getSQLState()));
    }
  }

  @Nonnull
  private Connection connect() throws SQLException {
    final VulnerabilityProperties.Database database = this.vulnerabilityProperties.getDb();
    final Properties credentials = new Properties();
    if (isConfigured(database.getUsername())) {
      credentials.setProperty("user", database.getUsername());
    }
    if (isConfigured(database.getPassword())) {
      credentials.setProperty("password", database.getPassword());
    }
    final Driver driver = isConfigured(database.getDriver())
                          ? this.driverFrom(database.getDriver(), database.getUrl())
                          : driverOnClassPath(database.getUrl());
    return driver.connect(database.getUrl(), credentials);
  }

  @Nonnull
  private Driver driverFrom(@Nonnull final String jar, @Nonnull final String url) throws SQLException {
    final URLClassLoader loader = this.driverLoaders.computeIfAbsent(jar, DependencyCheckAdapter::driverLoader);
    try {
      for (final Driver driver : ServiceLoader.load(Driver.class, loader)) {
        if (driver.acceptsURL(url)) {
          return driver;
        }
      }
    } catch (final ServiceConfigurationError error) {
      throw new SourceException("The JDBC driver %s cannot be loaded".formatted(jar), error);
    }
    throw new SourceException("No JDBC driver in %s accepts the URL".formatted(jar));
  }

  /** Above the platform loader only: the driver sees none of the application's classes, and they none of its. */
  @Nonnull
  private static URLClassLoader driverLoader(@Nonnull final String jar) {
    try {
      final Path path = Path.of(jar);
      if (Files.isRegularFile(path)) {
        return new URLClassLoader(new URL[]{path.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
      }
    } catch (final InvalidPathException | MalformedURLException exception) {
      throw new SourceException("The JDBC driver %s is no file path".formatted(jar), exception);
    }
    throw new SourceException("The JDBC driver %s does not exist".formatted(jar));
  }

  /** Only PostgreSQL's is on the class path, for the application's own database. */
  @Nonnull
  private static Driver driverOnClassPath(@Nonnull final String url) {
    try {
      return DriverManager.getDriver(url);
    } catch (final SQLException exception) {
      throw new SourceException("No JDBC driver on the class path accepts the URL; name one in VC_VULNERABILITIES_DB_DRIVER");
    }
  }

  @Nonnull
  private static List<CpeRange> ranges(@Nonnull final Connection connection, @Nonnull final String vendor,
                                       @Nonnull final String product) throws SQLException {
    final List<CpeRange> ranges = new ArrayList<>();
    try (PreparedStatement statement = connection.prepareStatement(RANGES)) {
      statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
      statement.setString(1, vendor);
      statement.setString(2, product);
      try (ResultSet rows = statement.executeQuery()) {
        while (rows.next()) {
          // A configuration that is not vulnerable names the platform a vulnerable one runs on, not a version.
          if (rows.getBoolean("vulnerable")) {
            ranges.add(range(rows));
          }
        }
      }
    }
    log.debug("Dependency-Check database listed {} vulnerable configurations for {}:{}", ranges.size(), vendor, product);
    return ranges;
  }

  @Nonnull
  private static CpeRange range(@Nonnull final ResultSet row) throws SQLException {
    // CVSS 3 where there is one, which NVD scores most of the time; version 4 and 2 only fill the gap.
    final BigDecimal v3 = row.getBigDecimal("v3BaseScore");
    final BigDecimal v4 = row.getBigDecimal("v4baseScore");
    final BigDecimal score = nonNull(v3) ? v3 : nonNull(v4) ? v4 : row.getBigDecimal("v2Score");
    final String severity = nonNull(v3) ? row.getString("v3BaseSeverity")
                                        : nonNull(v4) ? row.getString("v4baseSeverity") : row.getString("v2Severity");
    final String exploitedName = row.getString("vulnerabilityName");
    return new CpeRange(row.getString("cve"),
                        row.getString("version"),
                        row.getString("versionStartIncluding"),
                        row.getString("versionStartExcluding"),
                        row.getString("versionEndIncluding"),
                        row.getString("versionEndExcluding"),
                        score,
                        severity,
                        nonNull(row.getString("exploitedCve")),
                        nonBlank(exploitedName) ? exploitedName : shorten(row.getString("description")));
  }

  /** Optional: the lookup counts without it. */
  @Nullable
  private static Instant dataAsOf(@Nonnull final Connection connection) {
    try (Statement statement = connection.createStatement()) {
      statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
      try (ResultSet rows = statement.executeQuery(DATA_AS_OF)) {
        final String value = rows.next() ? rows.getString("value") : null;
        return nonBlank(value) ? Instant.parse(value.strip()) : null;
      }
    } catch (final SQLException exception) {
      log.debug("Dependency-Check database carries no data date: {}", exception.getSQLState());
      return null;
    } catch (final DateTimeParseException exception) {
      log.debug("Dependency-Check database states its data date in an unknown form: {}", exception.getParsedString());
      return null;
    }
  }

  @Nullable
  private static String shorten(@Nullable final String description) {
    if (nonBlank(description) && description.length() > SUMMARY_LENGTH) {
      return "%s…".formatted(description.substring(0, SUMMARY_LENGTH - 1).strip());
    }
    return description;
  }
}
