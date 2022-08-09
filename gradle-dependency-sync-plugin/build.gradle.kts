/*
 * Copyright (C) 2020-2022 Rick Busarow
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
  kotlin("jvm")
  alias(libs.plugins.gradle.plugin.publish)
  id("java-gradle-plugin")
  `maven-publish`
  alias(libs.plugins.dependency.guard)
}

dependencies {
  implementation(libs.semver)

  testImplementation(libs.hermit.core)
  testImplementation(libs.hermit.junit5)
  testImplementation(libs.kotest.assertions.core.jvm)
  testImplementation(libs.kotest.assertions.shared)
  testImplementation(libs.kotest.property.jvm)
  testImplementation(libs.kotest.runner.junit5.jvm)
  testImplementation(libs.junit.jupiter.api)
  testImplementation(libs.junit.jupiter.params)
  testImplementation(libs.junit.jupiter.engine)
}

kotlin {
  explicitApi()
}
dependencyGuard {
  configuration("runtimeClasspath") {
    modules = false
  }
}

tasks.withType<Test> {
  useJUnitPlatform()

  testLogging {
    events = setOf(
      org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED,
      org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
    )
    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    showExceptions = true
    showCauses = true
    showStackTraces = true
  }

  project
    .properties
    .asSequence()
    .filter { (key, value) ->
      key.startsWith("dependency-sync") && value != null
    }
    .forEach { (key, value) ->
      systemProperty(key, value!!)
    }
}

val testJvm by tasks.registering {
  dependsOn("test")
}

val buildTests by tasks.registering {
  dependsOn("testClasses")
}

java {
  // force Java 8 source when building java-only artifacts.
  // This is different than the Kotlin jvm target.
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

repositories {
  mavenCentral()
}

@Suppress("VariableNaming")
val VERSION: String by extra.properties

gradlePlugin {
  plugins {
    create("dependency-sync") {
      id = "com.rickbusarow.gradle-dependency-sync"
      group = "com.rickbusarow.gradle-dependency-sync"
      displayName = "Gradle Dependency Sync"
      implementationClass = "dependencysync.gradle.DependencySyncPlugin"
      version = VERSION
      description =
        "Automatically sync dependency declarations between a build.gradle.kts file and a .toml file"
    }
  }
}

pluginBundle {
  website = "https://github.com/RBusarow/gradle-dependency-sync"
  vcsUrl = "https://github.com/RBusarow/gradle-dependency-sync"
  description =
    "Automatically sync dependency declarations between a build.gradle.kts file and a .toml file"
  tags = listOf("dependencies", "dependabot", "gradle", "android", "kotlin")
}

tasks.create("setupPluginUploadFromEnvironment") {
  doLast {
    val key = System.getenv("GRADLE_PUBLISH_KEY")
    val secret = System.getenv("GRADLE_PUBLISH_SECRET")

    if (key == null || secret == null) {
      throw GradleException(
        "gradlePublishKey and/or gradlePublishSecret are not defined environment variables"
      )
    }

    System.setProperty("gradle.publish.key", key)
    System.setProperty("gradle.publish.secret", secret)
  }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>()
  .configureEach {
    kotlinOptions {
      allWarningsAsErrors = false

      val kotlinMajor = "1.5"

      languageVersion = kotlinMajor
      apiVersion = kotlinMajor

      val javaMajor = "11"

      jvmTarget = javaMajor
      sourceCompatibility = javaMajor
      targetCompatibility = javaMajor

      freeCompilerArgs = freeCompilerArgs + listOf(
        "-Xinline-classes",
        "-Xjvm-default=enable",
        "-Xsam-conversions=class",
        "-opt-in=kotlin.ExperimentalStdlibApi",
        "-opt-in=kotlin.RequiresOptIn",
        "-opt-in=kotlin.contracts.ExperimentalContracts",
        "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
        "-opt-in=kotlinx.coroutines.FlowPreview"
      )
    }
  }
