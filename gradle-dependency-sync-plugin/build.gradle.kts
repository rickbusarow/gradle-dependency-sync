/*
 * Copyright (C) 2021 Rick Busarow
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

plugins {
  kotlin("jvm")
  id("com.gradle.plugin-publish") version "0.19.0"
  id("java-gradle-plugin")
  `maven-publish`
}

dependencies {

  implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.6.10")
  implementation("org.jetbrains.kotlin:kotlin-reflect:1.6.10")
  implementation("net.swiftzer.semver:semver:1.1.2")

  testImplementation("com.rickbusarow.hermit:hermit-junit5:0.9.5")
  testImplementation("io.kotest:kotest-assertions-core-jvm:5.4.1")
  testImplementation("io.kotest:kotest-property-jvm:4.6.3")
  testImplementation("io.kotest:kotest-runner-junit5-jvm:4.6.3")
  testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
  testImplementation("org.junit.jupiter:junit-jupiter-params:5.8.1")
  testImplementation("org.junit.jupiter:junit-jupiter-engine:5.8.1")
}

kotlin {
  explicitApi()
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
      key.startsWith("modulecheck") && value != null
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

@Suppress("VariableNaming")
val VERSION: String by extra.properties

gradlePlugin {
  plugins {
    create("dependency-sync") {
      id = "com.rickbusarow.gradle-dependency-sync"
      group = "com.rickbusarow.gradle-dependency-sync"
      implementationClass = "dependencysync.gradle.DependencySyncPlugin"
      version = VERSION
    }
  }
}

pluginBundle {
  website = "https://github.com/RBusarow/gradle-dependency-sync"
  vcsUrl = "https://github.com/RBusarow/gradle-dependency-sync"
  description =
    "Automatically sync dependency declarations between a build.gradle.kts file and a .toml file"
  tags = listOf("dependencies", "dependabot")

  plugins {
    getByName("dependency-sync") {
      displayName = "Fast dependency graph validation for gradle"
    }
  }
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
