package one.epicsoft.vc.adapter;

import one.epicsoft.vc.config.VulnerabilityProperties;
import one.epicsoft.vc.data.CpeRange;
import one.epicsoft.vc.data.DepcheckLookup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A driver from outside, as an installation mounts one: H2, which the application does not carry. */
class DependencyCheckAdapterTest {

  private static final String DRIVER =
      Objects.requireNonNull(System.getProperty("vc.test.h2-driver"), "Run through Gradle: it hands over the H2 driver");

  // The columns read, typed as Dependency-Check 13 creates them for H2 — the backticks around value included.
  private static final List<String> SCHEMA = List.of(
      """
      CREATE TABLE vulnerability (id INT AUTO_INCREMENT PRIMARY KEY, cve VARCHAR(20) UNIQUE, description VARCHAR(8000),
                                  v2Severity VARCHAR(20), v2Score DECIMAL(3,1), v3BaseScore DECIMAL(3,1), v3BaseSeverity VARCHAR(20),
                                  v4baseScore DECIMAL(3,1), v4baseSeverity VARCHAR(15))""",
      "CREATE TABLE cpeEntry (id INT AUTO_INCREMENT PRIMARY KEY, part CHAR(1), vendor VARCHAR(255), product VARCHAR(255), version VARCHAR(255))",
      """
      CREATE TABLE software (cveid INT, cpeEntryId INT, versionEndExcluding VARCHAR(100), versionEndIncluding VARCHAR(100),
                             versionStartExcluding VARCHAR(100), versionStartIncluding VARCHAR(100), vulnerable BOOLEAN)""",
      "CREATE TABLE knownExploited (cveID VARCHAR(20) PRIMARY KEY, vulnerabilityName VARCHAR(500))",
      "CREATE TABLE properties (id VARCHAR(50) PRIMARY KEY, `value` VARCHAR(500))",
      "INSERT INTO properties (id, `value`) VALUES ('nvd.api.last.modified', '2026-10-26T22:10:00Z')",
      "INSERT INTO cpeEntry (id, part, vendor, product, version) VALUES (1, 'a', 'acme', 'orbit', '*'), (2, 'a', 'acme', 'orbit', '17.2')",
      """
      INSERT INTO vulnerability (id, cve, description, v3BaseScore, v3BaseSeverity) VALUES
        (1, 'CVE-2026-1001', 'A range of releases', 8.8, 'HIGH'), (2, 'CVE-2026-1002', 'Exactly this release', 7.5, 'HIGH'),
        (3, 'CVE-2026-1005', 'Only the platform', 9.8, 'CRITICAL')""",
      """
      INSERT INTO software (cveid, cpeEntryId, versionEndExcluding, versionEndIncluding, versionStartExcluding,
                            versionStartIncluding, vulnerable)
      VALUES (1, 1, '17.6', NULL, NULL, '17.0', TRUE), (2, 2, NULL, NULL, NULL, NULL, TRUE), (3, 2, NULL, NULL, NULL, NULL, FALSE)""",
      "INSERT INTO knownExploited (cveID, vulnerabilityName) VALUES ('CVE-2026-1002', 'Orbit exploited in the wild')");

  @Test
  void readsADatabaseThroughADriverFromOutside(@TempDir final Path directory) throws Exception {
    assertThrows(ClassNotFoundException.class, () -> Class.forName("org.h2.Driver"));
    final String url = createDatabase(directory);

    // Read only and never created, as the guide writes it for the file of a mirror.
    final DepcheckLookup lookup = adapter("%s;ACCESS_MODE_DATA=r;IFEXISTS=TRUE".formatted(url), DRIVER).lookUp("acme", "orbit");

    assertEquals(Instant.parse("2026-10-26T22:10:00Z"), lookup.dataAsOf());
    assertEquals(List.of("CVE-2026-1001", "CVE-2026-1002"), lookup.ranges().stream().map(CpeRange::cve).sorted().toList());
    assertTrue(lookup.ranges().stream().filter(CpeRange::knownExploited).allMatch(range -> "CVE-2026-1002".equals(range.cve())));
  }

  @Test
  void namesADriverThatIsNotThere(@TempDir final Path directory) {
    final String missing = directory.resolve("mariadb-java-client.jar").toString();

    final SourceException exception =
        assertThrows(SourceException.class, () -> adapter("jdbc:mariadb://localhost:3306/dependencycheck", missing).lookUp("acme", "orbit"));

    assertTrue(exception.getMessage().contains(missing));
  }

  @Test
  void asksForTheDriverOfADatabaseTheClassPathDoesNotKnow() {
    final SourceException exception =
        assertThrows(SourceException.class, () -> adapter("jdbc:mariadb://localhost:3306/dependencycheck", null).lookUp("acme", "orbit"));

    assertTrue(exception.getMessage().contains("VC_VULNERABILITIES_DB_DRIVER"));
  }

  private static String createDatabase(final Path directory) throws Exception {
    final String url = "jdbc:h2:file:%s".formatted(directory.resolve("odc").toAbsolutePath());
    try (URLClassLoader loader = new URLClassLoader(new URL[]{Path.of(DRIVER).toUri().toURL()}, ClassLoader.getPlatformClassLoader());
         Connection connection = ServiceLoader.load(Driver.class, loader).findFirst().orElseThrow().connect(url, credentials());
         Statement statement = connection.createStatement()) {
      for (final String sql : SCHEMA) {
        statement.execute(sql);
      }
    }
    return url;
  }

  private static DependencyCheckAdapter adapter(final String url, final String driver) {
    final VulnerabilityProperties properties = new VulnerabilityProperties();
    properties.getDb().setUrl(url);
    properties.getDb().setUsername("dcuser");
    properties.getDb().setPassword("DC-Pass1337!");
    properties.getDb().setDriver(driver);
    return new DependencyCheckAdapter(properties);
  }

  private static Properties credentials() {
    final Properties credentials = new Properties();
    credentials.setProperty("user", "dcuser");
    credentials.setProperty("password", "DC-Pass1337!");
    return credentials;
  }
}
