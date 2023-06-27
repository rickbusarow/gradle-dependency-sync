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
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import kotlin.text.RegexOption.MULTILINE

buildscript {
  dependencies {
    classpath(libs.kotlin.gradle.plugin)
  }
}

plugins {
  alias(libs.plugins.ben.manes.versions)
  alias(libs.plugins.dependencyAnalysis)
  alias(libs.plugins.detekt)
  alias(libs.plugins.dokka)
  alias(libs.plugins.doks)
  alias(libs.plugins.ktlint) apply false
  base
}

allprojects {
  tasks.register("fix") {
    dependsOn("ktlintFormat", "doks")
    dependsOn(tasks.matching { it.name == "dependencyGuardBaseline" })
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

  tasks.withType<Detekt>().configureEach {

    reports {
      xml.required.set(true)
      html.required.set(true)
      txt.required.set(false)
      sarif.required.set(true)
    }

    // Target version of the generated JVM bytecode. It is used for type resolution.
    this.jvmTarget = "1.8"
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

  tasks.withType<KotlinCompile>()
    .configureEach {
      kotlinOptions {

        allWarningsAsErrors = false

        val kotlinMajor = "1.6"

        languageVersion = kotlinMajor
        apiVersion = kotlinMajor

        val javaMajor = "1.8"

        jvmTarget = javaMajor

        freeCompilerArgs += listOf(
          "-Xjvm-default=all",
          "-Xallow-result-return-type",
          "-opt-in=kotlin.contracts.ExperimentalContracts",
          "-opt-in=kotlin.time.ExperimentalTime",
          "-opt-in=kotlin.RequiresOptIn",
          "-Xinline-classes"
        )
      }
    }
}

@Suppress("VariableNaming", "PropertyName")
val VERSION: String by extra.properties
val ktrules = libs.rickBusarow.ktrules.get()

allprojects {
  apply(plugin = "com.rickbusarow.ktlint")

  val target = this@allprojects

  target.dependencies {
    "ktlint"(ktrules)
  }

  target.tasks.withType(com.rickbusarow.ktlint.KtLintTask::class.java).configureEach {
    dependsOn(":updateEditorConfigVersion")
    mustRunAfter(
      target.tasks.matching { it.name == "apiDump" },
      target.tasks.matching { it.name == "dependencyGuard" },
      target.tasks.matching { it.name == "dependencyGuardBaseline" }
      // target.tasks.withType(KotlinApiBuildTask::class.java),
      // target.tasks.withType(KotlinApiCompareTask::class.java)
    )
  }
}

tasks.register("updateEditorConfigVersion") {

  val file = file(".editorconfig")

  doLast {
    val oldText = file.readText()

    val reg = """^(ktlint_kt-rules_project_version *?= *?)\S*$""".toRegex(MULTILINE)

    val newText = oldText.replace(reg, "$1$VERSION")

    if (newText != oldText) {
      file.writeText(newText)
    }
  }
}
