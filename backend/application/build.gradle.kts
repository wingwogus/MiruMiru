plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
}

repositories {
    mavenCentral()
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

dependencies {
    implementation(project(":domain"))
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.5.4"))
    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:3.5.4"))
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("com.querydsl:querydsl-jpa:5.0.0:jakarta")
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("io.jsonwebtoken:jjwt-api:0.11.5")
    implementation("io.jsonwebtoken:jjwt-impl:0.11.5")
    implementation("io.jsonwebtoken:jjwt-jackson:0.11.5")
    implementation("org.apache.commons:commons-lang3:3.14.0")
    implementation("commons-codec:commons-codec:1.16.0")
    implementation("org.springframework.boot:spring-boot-starter-security")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
