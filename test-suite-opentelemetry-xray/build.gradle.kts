plugins {
    id("java-library")
}
repositories {
    mavenCentral()
}

dependencies {
    testRuntimeOnly(mnLogging.logback.classic)

    testImplementation(projects.micronautTracingOpentelemetryHttp)
    testImplementation(libs.opentelemetry.exporter.otlp)
    testImplementation(libs.opentelemetry.aws.sdk)
    testImplementation(libs.managed.opentelemetry.contrib.aws.xray.propagator)
    testImplementation(libs.managed.opentelemetry.contrib.aws.resources)
    testRuntimeOnly(libs.junit.engine)
    testAnnotationProcessor(mn.micronaut.inject.java)
    testImplementation(mnTest.micronaut.test.junit5)

}
tasks.withType<Test> {
    useJUnitPlatform()
}

java {
    sourceCompatibility = JavaVersion.toVersion("17")
    targetCompatibility = JavaVersion.toVersion("17")
}