package com.moriah.skillhub;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.MountableFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;

import java.net.URI;
import java.nio.file.Paths;

/**
 * Shared base for every controller/repository integration test. Containers are genuine
 * process-wide singletons — started once in the static initializer below and never stopped by
 * this code (Ryuk reaps them at JVM exit) — so every test class in the same Maven run shares one
 * MySQL and one Redis instance, each on one stable port.
 * <p>
 * Deliberately <b>not</b> {@code @Testcontainers}/{@code @Container}: that JUnit5-managed
 * lifecycle stops a container in each test class's {@code afterAll}, then restarts a new one —
 * on a new port — for the next class. Spring's {@code ApplicationContext} cache doesn't know to
 * rebuild when that happens, so a second test class silently inherits a Hikari/Lettuce pool
 * still pointed at the first (now-dead) container's port. The static-initializer singleton
 * pattern is the documented workaround for sharing containers across multiple
 * {@code @SpringBootTest} classes — confirmed the hard way for MySQL in feature 02 (see the
 * recover-skill diagnosis in {@code progress-tracker.md}); applied to Redis from the start here.
 * <p>
 * {@code @ServiceConnection} wires {@code spring.datasource.*} (and Flyway — see below) and
 * {@code spring.data.redis.*} to each container automatically, per library-docs.md.
 * <p>
 * The database is explicitly named {@code moriah_skillhub} (not Testcontainers' default
 * {@code test}) so it is the exact same schema {@code docker/mysql-init/01-users.sql} provisions
 * {@code moriah_migrate}/{@code moriah_app} against. The container's default user is
 * {@code moriah_migrate} itself (not Testcontainers' generic {@code test}/{@code test}) so that
 * {@code @ServiceConnection} wires Flyway to it too — {@code afterMigrate.sql} needs
 * {@code moriah_migrate}'s {@code GRANT OPTION}, and plain {@code spring.flyway.*} properties are
 * silently ignored whenever {@code @ServiceConnection} is present (confirmed the hard way; see
 * progress-tracker.md). This has no effect on {@code DatabaseUserPrivilegesIT} — those tests
 * always open their own explicit {@code moriah_app}/{@code moriah_migrate} connections directly.
 * <p>
 * The container also runs the real {@code docker/mysql-init/01-users.sql} — the same script
 * docker-compose uses — via the standard {@code docker-entrypoint-initdb.d} mechanism.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("moriah_skillhub")
            .withUsername("moriah_migrate")
            .withPassword("migrate_dev_only")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(Paths.get("docker/mysql-init/01-users.sql").toAbsolutePath()),
                    "/docker-entrypoint-initdb.d/02-moriah-users.sql");

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    // No @ServiceConnection factory exists for S3/MinIO the way it does for MySQL/Redis — S3Client
    // isn't a Spring Boot auto-configured connection type — so this is wired manually below via
    // @DynamicPropertySource instead. Real MinIO, not a mocked SDK client: `.env.example`
    // provisions it in docker-compose specifically "wired at feature 08," matching this project's
    // established preference for real infrastructure over mocks wherever real infrastructure is
    // actually available (`/architect feature 08` decision).
    private static final String MINIO_ACCESS_KEY = "test-minio-access-key";
    private static final String MINIO_SECRET_KEY = "test-minio-secret-key";
    private static final String MINIO_BUCKET = "test-bucket";

    static final GenericContainer<?> MINIO = new GenericContainer<>("minio/minio:latest")
            .withExposedPorts(9000)
            .withEnv("MINIO_ROOT_USER", MINIO_ACCESS_KEY)
            .withEnv("MINIO_ROOT_PASSWORD", MINIO_SECRET_KEY)
            .withCommand("server", "/data");

    static {
        MYSQL.start();
        REDIS.start();
        MINIO.start();
        createTestBucket();
    }

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("moriah.storage.endpoint", IntegrationTestBase::minioEndpoint);
        registry.add("moriah.storage.bucket", () -> MINIO_BUCKET);
        registry.add("moriah.storage.access-key", () -> MINIO_ACCESS_KEY);
        registry.add("moriah.storage.secret-key", () -> MINIO_SECRET_KEY);
        registry.add("moriah.storage.region", () -> "us-east-1");
    }

    private static String minioEndpoint() {
        return "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
    }

    /** feature 22's {@code ReplicaDataSourceConfig} — no separate replica container in this test
     * suite (architecture.md's local {@code mysql-replica} docker-compose service already proves
     * real MySQL async replication; re-proving replication itself inside a fast
     * Testcontainers-per-run suite isn't this feature's job). Pointing the "replica" {@code
     * JdbcTemplate} at the same primary container/schema/user is a documented test-only
     * simplification — the replica-routing *code path* (a distinct, qualified {@code JdbcTemplate}
     * bean, wired into {@code MetricsService}/{@code AuditQueryService}/{@code
     * ExportGenerationService} instead of the default one) is still exercised for real. Same
     * {@code moriah_app}/{@code app_dev_only} credentials {@code docker/mysql-init/01-users.sql}
     * provisions in this same container. */
    @DynamicPropertySource
    static void replicaDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("moriah.datasource.replica.url", MYSQL::getJdbcUrl);
        registry.add("moriah.datasource.replica.username", () -> "moriah_app");
        registry.add("moriah.datasource.replica.password", () -> "app_dev_only");
    }

    /** MinIO doesn't auto-create a bucket — a throwaway {@link S3Client}, used once here and
     * never again, since {@link com.moriah.skillhub.common.config.S3Config}'s real bean isn't
     * available yet at this point in the container-singleton static initializer. Idempotent —
     * "already owned by you" is expected and ignored, not every test run starts a fresh
     * container (Ryuk only reaps at JVM exit, so a single `mvn verify` invocation reuses it
     * across every test class). */
    private static void createTestBucket() {
        try (S3Client bootstrapClient = S3Client.builder()
                .region(Region.US_EAST_1)
                .endpointOverride(URI.create(minioEndpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(MINIO_ACCESS_KEY, MINIO_SECRET_KEY)))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build()) {
            bootstrapClient.createBucket(b -> b.bucket(MINIO_BUCKET));
        } catch (BucketAlreadyOwnedByYouException e) {
            // Already created by an earlier test class in this same run — fine.
        }
    }
}
