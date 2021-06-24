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
  javaLibrary
  id("com.gradle.plugin-publish") version "0.15.0"
  id("java-gradle-plugin")
  `kotlin-dsl`
  `maven-publish`
}

dependencies {

  implementation(libs.kotlin.gradlePlugin)
  implementation(libs.kotlin.reflect)
  implementation(libs.semVer)

  testImplementation(libs.bundles.hermit)
  testImplementation(libs.bundles.jUnit)
  testImplementation(libs.bundles.kotest)
  testImplementation(libs.kotlinPoet)
}

gradlePlugin {
  plugins {
    create("dependency-sync") {
      id = "com.rickbusarow.gradle-dependency-sync"
      group = "com.rickbusarow.gradle-dependency-sync"
      implementationClass = "dependencysync.gradle.DependencySyncPlugin"
      version = libs.versions.versionName.get()
    }
  }
}

pluginBundle {
  website = "https://github.com/RBusarow/gradle-dependency-sync"
  vcsUrl = "https://github.com/RBusarow/gradle-dependency-sync"
  description = "Fast dependency graph validation for gradle"
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

kotlin {
  explicitApi()
}
