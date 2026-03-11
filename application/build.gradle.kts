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
    implementation("org.springframework.boot:spring-boot-starter-validation")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.5.4"))
    implementation("io.jsonwebtoken:jjwt-api:0.11.5")
    implementation("io.jsonwebtoken:jjwt-impl:0.11.5")
    implementation("io.jsonwebtoken:jjwt-jackson:0.11.5")
    implementation("org.apache.commons:commons-lang3:3.14.0")
    implementation("commons-codec:commons-codec:1.16.0")
    implementation("org.springframework.boot:spring-boot-starter-security")
    api("org.springframework.boot:spring-boot-starter-oauth2-client")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}