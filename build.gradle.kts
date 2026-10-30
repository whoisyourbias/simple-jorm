plugins {
    `java-library`
}

group = "com.example"
version = "0.1.0"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    testImplementation("org.springframework:spring-jdbc:6.2.12")
    testImplementation("commons-dbutils:commons-dbutils:1.8.1")
    testRuntimeOnly("com.mysql:mysql-connector-j:9.7.0")
    testImplementation("com.h2database:h2:2.4.240")
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.register<JavaExec>("mappingScalingBenchmark") {
    group = "verification"
    description = "Run row/column scaling experiments against local MySQL"
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.jorm.jdbc.lab.MappingScalingBenchmark")
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(21))
    })
    jvmArgs("-Xms512m", "-Xmx512m")
}

tasks.register<JavaExec>("libraryMappingBenchmark") {
    group = "verification"
    description = "Compare custom and automatic mapping through Spring JDBC and DbUtils"
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.jorm.jdbc.lab.LibraryMappingBenchmark")
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(21))
    })
    jvmArgs("-Xms512m", "-Xmx512m")
}

tasks.register<JavaExec>("databaseMappingBenchmark") {
    group = "verification"
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.jorm.jdbc.DatabaseMappingBenchmark")
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(21))
    })
    jvmArgs("-Xms256m", "-Xmx256m")
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<JavaExec>("mappingBenchmark") {
    group = "verification"
    description = "Compare manual and reflective mapping (exploratory benchmark)"
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.jorm.jdbc.MappingBenchmark")
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(21))
    })
    jvmArgs("-Xms256m", "-Xmx256m")
}
