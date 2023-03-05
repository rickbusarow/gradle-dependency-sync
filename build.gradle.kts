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

import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask

buildscript {
  dependencies {
    classpath(libs.kotlin.gradle.plugin)
  }
}

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
  alias(libs.plugins.littlerobots.vcu)
  alias(libs.plugins.ben.manes.versions)
  alias(libs.plugins.dependencyAnalysis)
  alias(libs.plugins.detekt)
  alias(libs.plugins.dokka)
  alias(libs.plugins.ktlint)
  base
}

versionCatalogUpdate {
  // sort the catalog by key (default is true)
  sortByKey.set(false)
  // Referenced that are pinned are not automatically updated.
  // They are also not automatically kept however (use keep for that).
  pin {
    // pins all libraries and plugins using the given versions
    // versions.add("my-version-name")

    // pins all libraries (not plugins) for the given groups
    // groups.add("com.somegroup")
  }
  keep {
    // versions.add("my-version-name")
    // groups.add("com.somegroup")

    keepUnusedVersions.set(true)
    keepUnusedLibraries.set(true)
    keepUnusedPlugins.set(false)
  }
}

allprojects {

  plugins.apply("io.gitlab.arturbosch.detekt")

  configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {

    parallel = true
    config = files("$rootDir/detekt/detekt-config.yml")
  }

  tasks.register("detektAll", Detekt::class.java) detektAll@{
    description = "runs the standard PSI Detekt as well as all type resolution tasks"

    dependsOn(
      tasks.withType(Detekt::class.java)
        .matching { it != this@detektAll }
    )
  }

  tasks.withType<DetektCreateBaselineTask> {

    include("**/*.kt", "**/*.kts")
    exclude("**/resources/**", "**/build/**", "**/src/test/java**")
  }

  tasks.withType<Detekt> {

    reports {
      xml.required.set(true)
      html.required.set(true)
      txt.required.set(false)
      sarif.required.set(true)
    }

    include("**/*.kt", "**/*.kts")
    exclude("**/resources/**", "**/build/**", "**/src/test/java**", "**/src/test/kotlin**")

    // Target version of the generated JVM bytecode. It is used for type resolution.
    this.jvmTarget = "11"
  }
}

fun isNonStable(version: String): Boolean {
  val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.uppercase().contains(it) }
  val regex = "^[0-9,.v-]+(-r)?$".toRegex()
  val isStable = stableKeyword || regex.matches(version)
  return isStable.not()
}

tasks.named(
  "dependencyUpdates",
  com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask::class.java
).configure {
  rejectVersionIf {
    isNonStable(candidate.version) && !isNonStable(currentVersion)
  }
}

allprojects {

  plugins.withId("org.jlleitschuh.gradle.ktlint") {

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
      debug.set(false)

      val ktlintVersion = extensions
        .getByType(VersionCatalogsExtension::class.java)
        .named("libs")
        .findVersion("ktlint-lib")
        .get().requiredVersion

      version.set(ktlintVersion)
      outputToConsole.set(true)
      enableExperimentalRules.set(true)
    }
  }
}
