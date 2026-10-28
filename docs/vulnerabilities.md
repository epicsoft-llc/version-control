# Known vulnerabilities

Optional. With a database that [OWASP Dependency-Check](https://github.com/dependency-check/DependencyCheck)
keeps current, Version Control looks up the known vulnerabilities (CVEs) of the recorded versions. Without one,
nothing of it appears.

- The component page lists the CVEs per environment — exploited ones first, then by CVSS score — with their
  severity, the mark of the CISA catalogue of known exploited vulnerabilities and the date of the data.
- The matrix marks a cell whose version has one, in red when one of them is exploited or critical.
- A CVE that is exploited or reaches `vc.vulnerabilities.task-min-score` (9.0) is a task, and the daily summary reports it.
  A component that updates itself gets no such task: its recorded version is not what runs. One held on its version
  does: holding is about updates, and a CVE found later still has to reach somebody.
- A CVE that does not matter for a component — the vulnerable part is not used, the component runs out — can be
  ignored on its page, with the reason required. It is no task any more, leaves the daily summary and the matrix, and
  waits folded under *Ignored* with its reason, where *Show again* takes the ignore back. Lookups do not bring it
  back; a new CVE of the same component still shows up.

Each component is looked up once a day and at the next scheduler tick after a new version is recorded; *Check now*
on the component page looks it up at once. When the database cannot be read, the findings of the last lookup stay.

## 1. A database

Version Control reads what Dependency-Check keeps current — H2, MariaDB, MySQL or PostgreSQL — read only, and from it
the tables `cpeEntry`, `software`, `vulnerability`, `knownExploited` and `properties` of schema 5.6. The database your
Dependency-Check runs already update serves as it is.

Without one, **<https://nvd.epicsoft.one/>** publishes ready-made databases every night, the CISA catalogue included —
the project [nvd-mirror](https://gitlab.com/epicsoft-networks/nvd-mirror), also on
[GitHub](https://github.com/epicsoft-llc/nvd-mirror):

```bash
# MariaDB or MySQL
curl -fsSLO https://nvd.epicsoft.one/db/dependencycheck-mysql.sql.gz
mysql -e "CREATE DATABASE dependencycheck CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
gunzip -c dependencycheck-mysql.sql.gz | mysql dependencycheck

# PostgreSQL
curl -fsSLO https://nvd.epicsoft.one/db/dependencycheck-postgres.sql.gz
createdb dependencycheck
gunzip -c dependencycheck-postgres.sql.gz | psql -d dependencycheck

# H2: one file, nothing to load
curl -fsSLO https://nvd.epicsoft.one/db/odc.mv.db.gz
gunzip odc.mv.db.gz
```

Each file has a `.sha256` next to it, and `db/db.json` names the Dependency-Check version, the schema and the H2
version the files belong to. Keeping the database current is up to you: load the dump of a later night, or let
Dependency-Check update it from the same mirror with `--updateonly --nvdDatafeed https://nvd.epicsoft.one/nvdcve-{0}.json.gz`
and the connection options from
[Using a Database Server](https://dependency-check.github.io/DependencyCheck/data/database.html).

## 2. The driver

The image carries the JDBC driver for PostgreSQL only, which it needs for its own database. Any other comes from
outside: put the JAR into a directory, mount it, and name it in `VC_VULNERABILITIES_DB_DRIVER`. It is loaded on its
own, apart from the application.

| Database | Driver, from Maven Central | URL |
|---|---|---|
| H2 | `com.h2database:h2` in the version that wrote the file | `jdbc:h2:file:/dependency-check/odc;ACCESS_MODE_DATA=r;IFEXISTS=TRUE` |
| MariaDB | `org.mariadb.jdbc:mariadb-java-client` | `jdbc:mariadb://db.example.com:3306/dependencycheck` |
| MySQL | `com.mysql:mysql-connector-j` | `jdbc:mysql://db.example.com:3306/dependencycheck` |
| PostgreSQL | built in | `jdbc:postgresql://db.example.com:5432/dependencycheck` |

```yaml
  core:
    environment:
      VC_VULNERABILITIES_DB_DRIVER: /drivers/mariadb-java-client-3.5.10.jar
    volumes:
      - ./drivers:/drivers:ro
```

H2 is a file, not a server: mount its directory read only as well (`./dependency-check:/dependency-check:ro`) and log
in with the account Dependency-Check creates in it, `dcuser` with the password `DC-Pass1337!`. A newer file replaces
the old one with `mv`; the next lookup reads it.

Tested with H2 2.4.240 on the database of nvd.epicsoft.one, MariaDB Connector/J 3.5.10 on MariaDB 13.0.2, MySQL
Connector/J 9.7.0 on MySQL 9.7.4, and PostgreSQL 18.

## 3. A read-only account

Version Control opens the connection read only; give it an account that can do nothing else either. H2 needs none,
its file is mounted read only.

```sql
-- MariaDB and MySQL
CREATE USER 'version-control'@'%' IDENTIFIED BY '<password>';
GRANT SELECT ON dependencycheck.* TO 'version-control'@'%';

-- PostgreSQL: the role once, the grants again after every load into a fresh database
CREATE ROLE version_control LOGIN PASSWORD '<password>';
GRANT CONNECT ON DATABASE dependencycheck TO version_control;
GRANT USAGE ON SCHEMA public TO version_control;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO version_control;
```

## 4. Configuration

```bash
VC_VULNERABILITIES_DB_URL=jdbc:mariadb://db.example.com:3306/dependencycheck
VC_VULNERABILITIES_DB_USERNAME=version-control
VC_VULNERABILITIES_DB_PASSWORD=<password>
VC_VULNERABILITIES_DB_DRIVER=/drivers/mariadb-java-client-3.5.10.jar
```

After a restart, `GET /api/v1/settings` answers `"vulnerabilities": true`, and the master data page asks each
component for its CPE vendor and product. A driver that is missing or does not take the URL shows on the component
page as the error of the lookup. `vc.vulnerabilities.check-interval` (1d) and
`vc.vulnerabilities.task-min-score` (9.0) can be changed like every other setting.

## 5. Vendor and product

A component is looked up by vendor and product as the CPE dictionary writes them, which is not always the name of
the image:

| Component | Vendor | Product |
|---|---|---|
| PostgreSQL | `postgresql` | `postgresql` |
| nginx | `f5` | `nginx` |
| Traefik | `traefik` | `traefik` |
| Grafana | `grafana` | `grafana` |
| Vault | `hashicorp` | `vault` |

The database itself says which names exist:

```sql
SELECT vendor, product, COUNT(*) AS entries
FROM cpeEntry
WHERE part = 'a' AND product LIKE '%nginx%'
GROUP BY vendor, product
ORDER BY entries DESC;
```

A name the database does not know finds nothing, and the component page then reads *No known vulnerability*.

## What it cannot see

- **CVEs without CPE data.** The NVD adds the affected products to a CVE only after publishing it; in October
  2026 more than half of that year's CVEs had none yet. Such a CVE matches no product.
- **Operating systems and hardware.** Only applications are looked up, CPE part `a`.
- **Fixes a distribution backports.** The upstream version is compared, so a Debian or Ubuntu package that was
  patched without a new upstream version is still reported.
- **Moving tags and digests.** `latest`, `17-alpine` or a digest name no version; such an environment is listed as
  not comparable.
- **Entries without a version.** An entry that names neither a version nor a range would hit every version ever
  released and is left out.
