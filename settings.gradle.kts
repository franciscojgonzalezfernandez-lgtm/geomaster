plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "geomaster"

include("core")
project(":core").projectDir = file("apps/core")
