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

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.springframework.boot:spring-boot-starter-jdbc")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	// JWT/JWKS parsing for the web→core service token (ADR-0017); Ed25519 is verified with the JDK.
	implementation("com.nimbusds:nimbus-jose-jwt:10.10")
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1")
	implementation("org.flywaydb:flyway-database-postgresql")
	runtimeOnly("org.postgresql:postgresql")
	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
	testImplementation("org.springframework.boot:spring-boot-starter-jdbc-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-postgresql")
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
