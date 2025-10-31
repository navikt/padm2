group = "no.nav.syfo"
version = "1.0.0"

val arenaDialogNotatVersion = "1.e1999cf"
val base64containerVersion = "1.5ac2176"
val dialogmeldingVersion = "2.0.1"
val fellesformat2Version = "1.0329dd1"
val flywayVersion = "11.15.0"
val hikariVersion = "7.0.2"
val ibmMqVersion = "9.4.4.0"
val jacksonVersion = "2.20.0"
val javaTimeAdapterVersion = "1.1.3"
val kafkaVersion = "3.9.1"
val kithApprecVersion = "2019.07.30-04-23-2a0d1388209441ec05d2e92a821eed4f796a3ae2"
val kithHodemeldingVersion = "2019.07.30-12-26-5c924ef4f04022bbb850aaf299eb8e4464c1ca6a"
val kluentVersion = "1.73"
val ktorVersion = "3.3.1"
val logbackVersion = "1.5.20"
val logstashEncoderVersion = "9.0"
val javaxAnnotationApiVersion = "1.3.2"
val javaxActivationVersion = "1.2.0"
val jaxbApiVersion = "2.4.0-b180830.0359"
val jaxbRuntimeVersion = "2.4.0-b180830.0438"
val jaxwsApiVersion = "2.3.1"
val jaxwsToolsVersion = "2.3.7"
val junitJupiterVersion = "5.11.3"
val micrometerRegistry = "1.12.13"
val mockkVersion = "1.14.6"
val nimbusJoseJwt = "10.5"
val pdfboxVersion = "2.0.24"
val postgresEmbedded = "2.1.1"
val postgresVersion = "42.7.8"
val postgresRuntimeVersion = "17.6.0"
val spek = "2.0.19"
val commonsCompressVersion = "1.28.0"

plugins {
    java
    kotlin("jvm") version "2.2.21"
    id("com.gradleup.shadow") version "8.3.6"
    id("org.jlleitschuh.gradle.ktlint") version "11.6.1"
}

repositories {
    mavenCentral()
    maven(url = "https://packages.confluent.io/maven/")
    maven(url = "https://jitpack.io")
    maven {
        url = uri("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
    }
}

dependencies {
    implementation(kotlin("reflect"))
    implementation(kotlin("stdlib"))

    implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion")
    implementation("io.ktor:ktor-server-call-id:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    implementation("io.ktor:ktor-client-jackson:$ktorVersion")
    implementation("io.ktor:ktor-client-auth-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")

    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:$micrometerRegistry")

    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    implementation("org.apache.pdfbox:pdfbox:$pdfboxVersion")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")

    implementation("no.nav.helse.xml:xmlfellesformat2:$fellesformat2Version")
    implementation("no.nav.helse.xml:kith-hodemelding:$kithHodemeldingVersion")
    implementation("no.nav.helse.xml:kith-apprec:$kithApprecVersion")
    implementation("no.nav.helse.xml:dialogmelding:$dialogmeldingVersion")
    implementation("no.nav.helse.xml:base64Container:$base64containerVersion")
    implementation("no.nav.helse.xml:arenaDialogNotat:$arenaDialogNotatVersion")

    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")

    implementation("javax.xml.ws:jaxws-api:$jaxwsApiVersion")
    implementation("javax.annotation:javax.annotation-api:$javaxAnnotationApiVersion")
    implementation("javax.xml.bind:jaxb-api:$jaxbApiVersion")
    implementation("org.glassfish.jaxb:jaxb-runtime:$jaxbRuntimeVersion")
    implementation("com.sun.activation:javax.activation:$javaxActivationVersion")
    implementation("com.sun.xml.ws:jaxws-tools:$jaxwsToolsVersion") {
        exclude(group = "com.sun.xml.ws", module = "policy")
    }
    implementation("com.migesok:jaxb-java-time-adapters:$javaTimeAdapterVersion")

    implementation("com.ibm.mq:com.ibm.mq.allclient:$ibmMqVersion")

    val excludeLog4j = fun ExternalModuleDependency.() {
        exclude(group = "log4j")
    }
    implementation("org.apache.kafka:kafka_2.13:$kafkaVersion", excludeLog4j)
    constraints {
        implementation("org.bitbucket.b_c:jose4j") {
            because("org.apache.kafka:kafka_2.13:$kafkaVersion -> https://github.com/advisories/GHSA-6qvw-249j-h44c")
            version {
                require("0.9.6")
            }
        }
        implementation("org.apache.commons:commons-compress") {
            because("org.apache.commons:commons-compress:1.22 -> https://www.cve.org/CVERecord?id=CVE-2012-2098")
            version {
                require(commonsCompressVersion)
            }
        }
    }

    testImplementation(kotlin("test"))
    testImplementation("com.nimbusds:nimbus-jose-jwt:$nimbusJoseJwt")
    testImplementation("org.amshove.kluent:kluent:$kluentVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
    testImplementation("io.zonky.test:embedded-postgres:$postgresEmbedded")
    testImplementation(platform("io.zonky.test.postgres:embedded-postgres-binaries-bom:$postgresRuntimeVersion"))
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("org.spekframework.spek2:spek-dsl-jvm:$spek") {
        exclude(group = "org.jetbrains.kotlin")
    }
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:$junitJupiterVersion")
    testRuntimeOnly("org.spekframework.spek2:spek-runner-junit5:$spek") {
        exclude(group = "org.jetbrains.kotlin")
    }
}

kotlin {
    jvmToolchain(21)
}

tasks {

    jar {
        manifest.attributes["Main-Class"] = "no.nav.syfo.BootstrapKt"
    }

    shadowJar {
        mergeServiceFiles()
        archiveBaseName.set("app")
        archiveClassifier.set("")
        archiveVersion.set("")
    }

    test {
        useJUnitPlatform {
            includeEngines("spek2", "junit-jupiter")
        }
        testLogging {
            showStandardStreams = true
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
}
