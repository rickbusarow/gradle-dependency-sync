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

import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask

buildscript {
  dependencies {
    classpath(libs.kotlin.gradle.plugin)
    classpath(libs.ktlint.gradle)
  }
}

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
  alias(libs.plugins.littlerobots.vcu)
  alias(libs.plugins.ben.manes.versions)
  alias(libs.plugins.dependencyAnalysis)
  alias(libs.plugins.detekt)
  alias(libs.plugins.dokka)
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

detekt {

  parallel = true
  config = files("$rootDir/detekt/detekt-config.yml")
}

tasks.withType<DetektCreateBaselineTask> {

  setSource(files(rootDir))

  include("**/*.kt", "**/*.kts")
  exclude("**/resources/**", "**/build/**", "**/src/test/java**")

  // Target version of the generated JVM bytecode. It is used for type resolution.
  this.jvmTarget = "1.8"
}

tasks.withType<Detekt> {

  setSource(files(rootDir))

  reports {
    xml.required.set(true)
    html.required.set(true)
    txt.required.set(false)
    sarif.required.set(true)
  }

  include("**/*.kt", "**/*.kts")
  exclude("**/resources/**", "**/build/**", "**/src/test/java**", "**/src/test/kotlin**")

  // Target version of the generated JVM bytecode. It is used for type resolution.
  this.jvmTarget = "1.8"
}

fun isNonStable(version: String): Boolean {
  val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.toUpperCase().contains(it) }
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

  apply(plugin = "org.jlleitschuh.gradle.ktlint")

  configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
    debug.set(false)
    version.set("0.45.2")
    outputToConsole.set(true)
    enableExperimentalRules.set(true)
    disabledRules.set(
      setOf(
        "max-line-length", // manually formatting still does this, and KTLint will still wrap long chains when possible
        "filename", // same as Detekt's MatchingDeclarationName, but Detekt's version can be suppressed and this can't
        "experimental:argument-list-wrapping", // doesn't work half the time
        "experimental:no-empty-first-line-in-method-block", // code golf...
        // This can be re-enabled once 0.46.0 is released
        // https://github.com/pinterest/ktlint/issues/1435
        "experimental:type-parameter-list-spacing",
        // added in 0.46.0
        "experimental:function-signature"
      )
    )
  }
}
