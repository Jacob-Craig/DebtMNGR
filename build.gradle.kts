plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("plugin.jpa") version "2.3.21"
}

group = "com.jacobcraig"
version = "0.0.1-SNAPSHOT"
description = "DebtMNGR"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-thymeleaf-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Database Management Tasks (Cross-platform for macOS, Linux, and Windows)
tasks.register("dbDump") {
    group = "database"
    description = "Dumps database state to a SQL file (default: backup.sql, override with -Pfile=...)"
    doLast {
        val destFile = file(project.findProperty("file") as? String ?: "backup.sql")
        val process = ProcessBuilder("docker", "compose", "exec", "-T", "db", "pg_dump", "-U", "postgres", "-d", "debtmngr_db")
            .redirectOutput(destFile)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .directory(projectDir)
            .start()
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw GradleException("Failed to dump database (exit code $exitCode). Ensure the database container is running ('docker compose up -d').")
        }
        println("Database state dumped successfully to: ${destFile.absolutePath}")
    }
}

tasks.register("dbRestore") {
    group = "database"
    description = "Restores database state from a SQL file (default: backup.sql, override with -Pfile=...)"
    doLast {
        val srcFile = file(project.findProperty("file") as? String ?: "backup.sql")
        if (!srcFile.exists()) {
            throw GradleException("Backup file does not exist: ${srcFile.absolutePath}")
        }
        println("Resetting schema before restore...")
        val clearProcess = ProcessBuilder("docker", "compose", "exec", "-T", "db", "psql", "-U", "postgres", "-d", "debtmngr_db", "-c", "DROP SCHEMA public CASCADE; CREATE SCHEMA public;")
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .directory(projectDir)
            .start()
        val clearExit = clearProcess.waitFor()
        if (clearExit != 0) {
            throw GradleException("Failed to clear database before restore (exit code $clearExit).")
        }

        println("Restoring database from ${srcFile.name}...")
        val restoreProcess = ProcessBuilder("docker", "compose", "exec", "-T", "db", "psql", "-U", "postgres", "-d", "debtmngr_db")
            .redirectInput(srcFile)
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .directory(projectDir)
            .start()
        val restoreExit = restoreProcess.waitFor()
        if (restoreExit != 0) {
            throw GradleException("Failed to restore database (exit code $restoreExit).")
        }
        println("Database restored successfully from: ${srcFile.absolutePath}")
    }
}

tasks.register("dbClear") {
    group = "database"
    description = "Clears the database by dropping and recreating the public schema"
    doLast {
        val process = ProcessBuilder("docker", "compose", "exec", "-T", "db", "psql", "-U", "postgres", "-d", "debtmngr_db", "-c", "DROP SCHEMA public CASCADE; CREATE SCHEMA public;")
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .directory(projectDir)
            .start()
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw GradleException("Failed to clear database (exit code $exitCode). Ensure the database container is running ('docker compose up -d').")
        }
        println("Database schema cleared successfully. (Hibernate will recreate schema on next app run).")
    }
}

