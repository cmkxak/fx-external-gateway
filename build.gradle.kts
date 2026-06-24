plugins {
    java
    id("org.springframework.boot") version "3.5.6"   // ← 사내 Nexus에 있는 정확한 3.5.x 패치로 조정
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.hectofinancial"
version = "0.0.1-SNAPSHOT"
description = "fx remittance web api (Thunes MoneyTransfer V2 연동)"

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

repositories {
    // 폐쇄망: 사내 Nexus 미러를 가리키게 설정 (환경 표준 따름)
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
