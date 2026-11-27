plugins {
	java
	id("org.springframework.boot") version "4.1.1"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.geomaster"
version = "0.1.0-SNAPSHOT"
description = "GEOMASTER core domain service: scans, extraction, GEO Score, agents, RAG"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

// OpenTelemetry Java agent (ADR-0012), resolved from Maven Central like any dependency and copied
// next to the boot jar; the Docker image runs core with -javaagent.
val otelAgent: Configuration by configurations.creating { isTransitive = false }

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.springframework.boot:spring-boot-starter-jdbc")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	// JWT/JWKS parsing for the web→core service token (ADR-0017); Ed25519 is verified with the JDK.
	implementation("com.nimbusds:nimbus-jose-jwt:10.10")
	implementation("com.openai:openai-java:4.101.0") {
		// The SDK ships non-Jakarta swagger-annotations 2.2.31; the same classes come from springdoc's
		// swagger-annotations-jakarta 2.2.55, and the older copy breaks /v3/api-docs (NoSuchMethodError).
		exclude(group = "io.swagger.core.v3", module = "swagger-annotations")
	}
	// Claude extractor (extraction-analysis T4) and the later Claude engine (scan-engine T9).
	implementation("com.anthropic:anthropic-java:3.8.0") {
		exclude(group = "io.swagger.core.v3", module = "swagger-annotations")
	}
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1")
	implementation("org.flywaydb:flyway-database-postgresql")
	// Custom spans per scan and per LLM call (T11); the agent supplies the SDK at runtime.
	implementation("io.opentelemetry:opentelemetry-api")
	otelAgent("io.opentelemetry.javaagent:opentelemetry-javaagent:2.34.0")
	runtimeOnly("org.postgresql:postgresql")
	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
	testImplementation("org.springframework.boot:spring-boot-starter-jdbc-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-postgresql")
	testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
}

// Plan limits have one source of truth: packages/plans/plans.json (REQ-11, ADR-0010).
// Copy it onto the classpath as plans/plans.json so the jar (and the Docker image) carry it.
tasks.processResources {
	from(rootProject.layout.projectDirectory.file("packages/plans/plans.json")) {
		into("plans")
	}
}

// Regenerate packages/api-contract/openapi.yaml from the code (ADR-0016). Same test that CI runs
// as the drift check, in write mode. Needs Docker (Testcontainers).
tasks.register<Test>("updateApiContract") {
	description = "Regenerates packages/api-contract/openapi.yaml from springdoc."
	group = "documentation"
	testClassesDirs = sourceSets["test"].output.classesDirs
	classpath = sourceSets["test"].runtimeClasspath
	useJUnitPlatform()
	filter { includeTestsMatching("com.geomaster.core.api.OpenApiContractTest") }
	systemProperty("updateApiContract", "true")
	outputs.upToDateWhen { false }
}

val copyOtelAgent by tasks.registering(Copy::class) {
	description = "Copies the OpenTelemetry Java agent to build/otel/opentelemetry-javaagent.jar."
	from(otelAgent)
	into(layout.buildDirectory.dir("otel"))
	rename { "opentelemetry-javaagent.jar" }
}

tasks.bootJar {
	finalizedBy(copyOtelAgent)
}
