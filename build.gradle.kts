import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.apache.tools.ant.taskdefs.condition.Os

group = "no.nav.syfo"
version = "1.0.0"

object Versions {
    const val arenaDialogNotatVersion = "1.e1999cf"
    const val base64containerVersion = "1.5ac2176"
    const val dialogmeldingVersion = "1.5d21db9"
    const val fellesformat2Version = "1.0329dd1"
    const val flywayVersion = "9.22.3"
    const val hikariVersion = "5.0.1"
    const val ibmMqVersion = "9.3.4.1"
    const val jacksonVersion = "2.16.0"
    const val javaTimeAdapterVersion = "1.1.3"
    const val kafkaEmbeddedVersion = "3.2.1"
    const val kafkaVersion = "3.6.0"
    const val kithApprecVersion = "2019.07.30-04-23-2a0d1388209441ec05d2e92a821eed4f796a3ae2"
    const val kithHodemeldingVersion = "2019.07.30-12-26-5c924ef4f04022bbb850aaf299eb8e4464c1ca6a"
    const val kluentVersion = "1.73"
    const val ktorVersion = "2.3.7"
    const val logbackVersion = "1.4.14"
    const val logstashEncoderVersion = "7.4"
    const val javaxAnnotationApiVersion = "1.3.2"
    const val javaxActivationVersion = "1.2.0"
    const val jaxbApiVersion = "2.4.0-b180830.0359"
    const val jaxbRuntimeVersion = "2.4.0-b180830.0438"
    const val jaxwsApiVersion = "2.3.1"
    const val jaxwsToolsVersion = "2.3.7"
    const val junitJupiterVersion = "5.8.2"
    const val micrometerRegistry = "1.12.0"
    const val mockkVersion = "1.13.8"
    const val nimbusJoseJwt = "9.37.2"
    const val pdfboxVersion = "2.0.24"
    val postgresEmbedded = if (Os.isFamily(Os.FAMILY_MAC)) "1.0.0" else "0.13.4"
    const val postgresVersion = "42.6.0"
    const val scala = "2.13.9"
    const val spek = "2.0.19"
}

plugins {
    java
    kotlin("jvm") version "1.9.22"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("org.jlleitschuh.gradle.ktlint") version "11.4.2"
}

val githubUser: String by project
val githubPassword: String by project

repositories {
    mavenCentral()
    maven(url = "https://packages.confluent.io/maven/")
    maven(url = "https://jitpack.io")
    maven {
        url = uri("https://maven.pkg.github.com/navikt/syfo-xml-codegen")
        credentials {
            username = githubUser
            password = githubPassword
        }
    }
}

dependencies {
    implementation(kotlin("reflect"))
    implementation(kotlin("stdlib"))

    implementation("io.ktor:ktor-server-auth-jwt:${Versions.ktorVersion}")
    implementation("io.ktor:ktor-server-call-id:${Versions.ktorVersion}")
    implementation("io.ktor:ktor-server-status-pages:${Versions.ktorVersion}")
    implementation("io.ktor:ktor-server-netty:${Versions.ktorVersion}")
    implementation("io.ktor:ktor-server-content-negotiation:${Versions.ktorVersion}")
    implementation("io.ktor:ktor-client-apache:${Versions.ktorVersion}")
    implementation("io.ktor:ktor-client-jackson:${Versions.ktorVersion}")
    implementation("io.ktor:ktor-client-auth-jvm:${Versions.ktorVersion}")
    implementation("io.ktor:ktor-client-content-negotiation:${Versions.ktorVersion}")
    implementation("io.ktor:ktor-serialization-jackson:${Versions.ktorVersion}")

    implementation("io.ktor:ktor-server-metrics-micrometer:${Versions.ktorVersion}")
    implementation("io.micrometer:micrometer-registry-prometheus:${Versions.micrometerRegistry}")

    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:${Versions.jacksonVersion}")

    implementation("org.apache.pdfbox:pdfbox:${Versions.pdfboxVersion}")

    implementation("ch.qos.logback:logback-classic:${Versions.logbackVersion}")
    implementation("net.logstash.logback:logstash-logback-encoder:${Versions.logstashEncoderVersion}")

    implementation("no.nav.helse.xml:xmlfellesformat2:${Versions.fellesformat2Version}")
    implementation("no.nav.helse.xml:kith-hodemelding:${Versions.kithHodemeldingVersion}")
    implementation("no.nav.helse.xml:kith-apprec:${Versions.kithApprecVersion}")
    implementation("no.nav.helse.xml:dialogmelding:${Versions.dialogmeldingVersion}")
    implementation("no.nav.helse.xml:base64Container:${Versions.base64containerVersion}")
    implementation("no.nav.helse.xml:arenaDialogNotat:${Versions.arenaDialogNotatVersion}")

    implementation("org.postgresql:postgresql:${Versions.postgresVersion}")
    implementation("com.zaxxer:HikariCP:${Versions.hikariVersion}")
    implementation("org.flywaydb:flyway-core:${Versions.flywayVersion}")

    implementation("javax.xml.ws:jaxws-api:${Versions.jaxwsApiVersion}")
    implementation("javax.annotation:javax.annotation-api:${Versions.javaxAnnotationApiVersion}")
    implementation("javax.xml.bind:jaxb-api:${Versions.jaxbApiVersion}")
    implementation("org.glassfish.jaxb:jaxb-runtime:${Versions.jaxbRuntimeVersion}")
    implementation("com.sun.activation:javax.activation:${Versions.javaxActivationVersion}")
    implementation("com.sun.xml.ws:jaxws-tools:${Versions.jaxwsToolsVersion}") {
        exclude(group = "com.sun.xml.ws", module = "policy")
    }
    implementation("com.migesok:jaxb-java-time-adapters:${Versions.javaTimeAdapterVersion}")

    implementation("com.ibm.mq:com.ibm.mq.allclient:${Versions.ibmMqVersion}")

    val excludeLog4j = fun ExternalModuleDependency.() {
        exclude(group = "log4j")
    }
    implementation("org.apache.kafka:kafka_2.13:${Versions.kafkaVersion}", excludeLog4j)
    constraints {
        implementation("org.apache.zookeeper:zookeeper") {
            because("org.apache.kafka:kafka_2.13:${Versions.kafkaVersion} -> https://www.cve.org/CVERecord?id=CVE-2023-44981")
            version {
                require("3.8.3")
            }
        }
        implementation("org.scala-lang:scala-library") {
            because("org.apache.kafka:kafka_2.13:${Versions.kafkaVersion} -> https://www.cve.org/CVERecord?id=CVE-2022-36944")
            version {
                require(Versions.scala)
            }
        }
    }
    testImplementation("no.nav:kafka-embedded-env:${Versions.kafkaEmbeddedVersion}", excludeLog4j)
    constraints {
        implementation("org.yaml:snakeyaml") {
            because("no.nav:kafka-embedded-env:${Versions.kafkaEmbeddedVersion} -> https://advisory.checkmarx.net/advisory/vulnerability/CVE-2022-25857/")
            version {
                require("1.33")
            }
        }
        implementation("org.eclipse.jetty.http2:http2-server") {
            because("no.nav:kafka-embedded-env:${Versions.kafkaEmbeddedVersion} -> https://advisory.checkmarx.net/advisory/vulnerability/CVE-2022-2048/")
            version {
                require("9.4.48.v20220622")
            }
        }
        implementation("com.google.protobuf:protobuf-java") {
            because("no.nav:kafka-embedded-env:${Versions.kafkaEmbeddedVersion} -> https://cwe.mitre.org/data/definitions/400.html")
            version {
                require("3.21.7")
            }
        }
    }

    testImplementation(kotlin("test"))
    testImplementation("com.nimbusds:nimbus-jose-jwt:${Versions.nimbusJoseJwt}")
    testImplementation("org.amshove.kluent:kluent:${Versions.kluentVersion}")
    testImplementation("org.junit.jupiter:junit-jupiter-api:${Versions.junitJupiterVersion}")
    testImplementation("org.junit.jupiter:junit-jupiter-params:${Versions.junitJupiterVersion}")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:${Versions.junitJupiterVersion}")
    testImplementation("io.mockk:mockk:${Versions.mockkVersion}")
    testImplementation("io.ktor:ktor-client-mock:${Versions.ktorVersion}")
    testImplementation("com.opentable.components:otj-pg-embedded:${Versions.postgresEmbedded}")
    testImplementation("io.ktor:ktor-server-test-host:${Versions.ktorVersion}")
    testImplementation("org.spekframework.spek2:spek-dsl-jvm:${Versions.spek}") {
        exclude(group = "org.jetbrains.kotlin")
    }
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:${Versions.junitJupiterVersion}")
    testRuntimeOnly("org.spekframework.spek2:spek-runner-junit5:${Versions.spek}") {
        exclude(group = "org.jetbrains.kotlin")
    }
}

kotlin {
    jvmToolchain(17)
}

tasks {
    withType<Jar> {
        manifest.attributes["Main-Class"] = "no.nav.syfo.BootstrapKt"
    }

    create("printVersion") {
        doLast {
            println(project.version)
        }
    }

    withType<ShadowJar> {
        archiveBaseName.set("app")
        archiveClassifier.set("")
        archiveVersion.set("")
    }

    withType<Test> {
        useJUnitPlatform {
            includeEngines("spek2", "junit-vintage")
        }
        testLogging {
            showStandardStreams = true
        }
    }
}
