package dependencysync.gradle

import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

class IntegrationTests : BaseTest() {

  @Test
  fun `everything in sync should not be changed`() = test(
    toml = """
      [versions]
      androidTools = "4.2.2"

      [libraries]
      androidGradlePlugin = { module = "com.android.tools.build:gradle", version.ref = "androidTools" }

      androidx-activity-ktx = "androidx.activity:activity-ktx:1.2.3"
    """.trimIndent(),
    gradle = """
      plugins {
        id("com.rickbusarow.gradle-dependency-sync")
      }

      dependencies {
        dependencySync("com.android.tools.build:gradle:4.2.2")
        dependencySync("androidx.activity:activity-ktx:1.2.3")
      }
    """.trimIndent()
  ) {

    buildResult.shouldSucceed()

    buildResult.output shouldNotContain "updated Toml dependency declaration"

    tomlText() shouldBe tomlInput
    buildText() shouldBe buildInput
  }

  @Test
  fun `unsorted toml entries should be sorted`() = test(
    toml = """
      [versions]
      androidTools = "4.2.2"

      [libraries]

      androidx-activity-ktx = "androidx.activity:activity-ktx:1.2.3"

      androidGradlePlugin = { module = "com.android.tools.build:gradle", version.ref = "androidTools" }

    """.trimIndent(),
    gradle = """
      plugins {
        id("com.rickbusarow.gradle-dependency-sync")
      }

      dependencies {
        dependencySync("com.android.tools.build:gradle:4.2.2")
        dependencySync("androidx.activity:activity-ktx:1.2.3")
      }
    """.trimIndent()
  ) {

    buildResult.shouldSucceed()

    buildResult.output shouldNotContain "updated Toml dependency declaration"

    tomlText() shouldBe """
      [versions]
      androidTools = "4.2.2"

      [libraries]
      androidGradlePlugin = { module = "com.android.tools.build:gradle", version.ref = "androidTools" }

      androidx-activity-ktx = "androidx.activity:activity-ktx:1.2.3"
    """.trimIndent()
    buildText() shouldBe buildInput
  }

  @Test
  fun `missing toml dependency should be added`() = test(
    toml = """
      [versions]
      androidTools = "4.2.2"

      [libraries]
      androidGradlePlugin = { module = "com.android.tools.build:gradle", version.ref = "androidTools" }
    """.trimIndent(),
    gradle = """
      plugins {
        id("com.rickbusarow.gradle-dependency-sync")
      }

      dependencies {
        dependencySync("com.android.tools.build:gradle:4.2.2")
        dependencySync("androidx.activity:activity-ktx:1.2.3")
      }
    """.trimIndent()
  ) {

    buildResult.shouldSucceed()

    buildResult.output shouldContain "added new Toml dependency declaration: \t\t androidx.activity:activity-ktx:1.2.3"

    tomlText() shouldBe """
      [versions]
      androidTools = "4.2.2"

      [libraries]
      androidGradlePlugin = { module = "com.android.tools.build:gradle", version.ref = "androidTools" }

      androidx-activity-ktx = "androidx.activity:activity-ktx:1.2.3"
    """.trimIndent()
  }

  @Test
  fun `missing build file dependency should be added`() = test(
    toml = """
      [versions]
      androidTools = "4.2.2"

      [libraries]
      androidGradlePlugin = { module = "com.android.tools.build:gradle", version.ref = "androidTools" }

      androidx-activity-ktx = "androidx.activity:activity-ktx:1.2.3"
    """.trimIndent(),
    gradle = """
      plugins {
        id("com.rickbusarow.gradle-dependency-sync")
      }

      dependencies {
        dependencySync("com.android.tools.build:gradle:4.2.2")
      }
    """.trimIndent()
  ) {

    buildResult.shouldSucceed()

    buildResult.output shouldContain "added new build file dependency declaration: \t\t androidx.activity:activity-ktx:1.2.3"

    buildText() shouldBe """
      plugins {
        id("com.rickbusarow.gradle-dependency-sync")
      }

      dependencies {
        dependencySync("androidx.activity:activity-ktx:1.2.3")
        dependencySync("com.android.tools.build:gradle:4.2.2")
      }
    """.trimIndent()
  }

  @Test
  fun `out-of-date toml dependency should be updated`() = test(
    toml = """
      [versions]
      androidTools = "4.2.1"

      [libraries]
      androidGradlePlugin = { module = "com.android.tools.build:gradle", version.ref = "androidTools" }

      androidx-activity-ktx = "androidx.activity:activity-ktx:1.2.3"
    """.trimIndent(),
    gradle = """
      plugins {
        id("com.rickbusarow.gradle-dependency-sync")
      }

      dependencies {
        dependencySync("com.android.tools.build:gradle:4.2.2")
        dependencySync("androidx.activity:activity-ktx:1.2.3")
      }
    """.trimIndent()
  ) {

    buildResult.shouldSucceed()

    buildResult.output shouldContain """
      updated Toml dependency declaration
            old: com.android.tools.build:gradle:4.2.1
            new: com.android.tools.build:gradle:4.2.2
    """.trimIndent()

    tomlText() shouldBe """
      [versions]
      androidTools = "4.2.2"

      [libraries]
      androidGradlePlugin = { module = "com.android.tools.build:gradle", version.ref = "androidTools" }

      androidx-activity-ktx = "androidx.activity:activity-ktx:1.2.3"
    """.trimIndent()
  }

  @Test
  fun `out-of-date gradle dependency should be updated`() = test(
    toml = """
      [versions]
      androidTools = "4.2.2"

      [libraries]
      androidGradlePlugin = { module = "com.android.tools.build:gradle", version.ref = "androidTools" }

      androidx-activity-ktx = "androidx.activity:activity-ktx:1.2.3"
    """.trimIndent(),
    gradle = """
      plugins {
        id("com.rickbusarow.gradle-dependency-sync")
      }

      dependencies {
        dependencySync("com.android.tools.build:gradle:4.2.1")
        dependencySync("androidx.activity:activity-ktx:1.2.3")
      }
    """.trimIndent()
  ) {

    buildResult.shouldSucceed()

    buildResult.output shouldContain """
      updated build file dependency declaration
            old: com.android.tools.build:gradle:4.2.1
            new: com.android.tools.build:gradle:4.2.2
    """.trimIndent()

    buildText() shouldBe """
      plugins {
        id("com.rickbusarow.gradle-dependency-sync")
      }

      dependencies {
        dependencySync("com.android.tools.build:gradle:4.2.2")
        dependencySync("androidx.activity:activity-ktx:1.2.3")
      }
    """.trimIndent()
  }

  @Test
  fun `out-of-date non-semver gradle dependency should be updated`() = test(
    toml = """
      [versions]
      google-dagger = "2.39.1"

      [libraries]

      google-dagger-api = { module = "com.google.dagger:dagger", version.ref = "google-dagger" }
      google-dagger-compiler = { module = "com.google.dagger:dagger-compiler", version.ref = "google-dagger" }
    """.trimIndent(),
    gradle = """
      plugins {
        id("com.rickbusarow.gradle-dependency-sync")
      }

      dependencies {
        dependencySync("com.google.dagger:dagger-compiler:2.39.1")
        dependencySync("com.google.dagger:dagger:2.39")
      }
    """.trimIndent()
  ) {

    buildResult.shouldSucceed()

    buildResult.output shouldContain """
      updated build file dependency declaration
            old: com.google.dagger:dagger:2.39
            new: com.google.dagger:dagger:2.39.1
    """.trimIndent()

    buildText() shouldBe """
      plugins {
        id("com.rickbusarow.gradle-dependency-sync")
      }

      dependencies {
        dependencySync("com.google.dagger:dagger-compiler:2.39.1")
        dependencySync("com.google.dagger:dagger:2.39.1")
      }
    """.trimIndent()
  }

  @Test
  fun `complex toml rc02 should be updated to stable`() = test(
    toml = """
      [versions]
      androidTools = "7.0.0-rc02"

      [libraries]
      androidGradlePlugin = { module = "com.android.tools.build:gradle", version.ref = "androidTools" }
    """.trimIndent(),
    gradle = """
      plugins {
        id("com.rickbusarow.gradle-dependency-sync")
      }

      dependencies {
        dependencySync("com.android.tools.build:gradle:7.0.0")
      }
    """.trimIndent()
  ) {

    buildResult.shouldSucceed()

    buildResult.output shouldContain """
      updated Toml dependency declaration
            old: com.android.tools.build:gradle:7.0.0-rc02
            new: com.android.tools.build:gradle:7.0.0
    """.trimIndent()

    buildText() shouldBe """
      plugins {
        id("com.rickbusarow.gradle-dependency-sync")
      }

      dependencies {
        dependencySync("com.android.tools.build:gradle:7.0.0")
      }
    """.trimIndent()

    tomlText() shouldBe """
      [versions]
      androidTools = "7.0.0"

      [libraries]
      androidGradlePlugin = { module = "com.android.tools.build:gradle", version.ref = "androidTools" }
    """.trimIndent()
  }

  @Test
  fun `simple toml rc02 should be updated to stable`() = test(
    toml = """
      [versions]

      [libraries]
      androidGradlePlugin = "com.android.tools.build:gradle:7.0.0-rc02"
    """.trimIndent(),
    gradle = """
      plugins {
        id("com.rickbusarow.gradle-dependency-sync")
      }

      dependencies {
        dependencySync("com.android.tools.build:gradle:7.0.0")
      }
    """.trimIndent()
  ) {

    buildResult.shouldSucceed()

    buildResult.output shouldContain """
      updated Toml dependency declaration
            old: com.android.tools.build:gradle:7.0.0-rc02
            new: com.android.tools.build:gradle:7.0.0
    """.trimIndent()

    buildText() shouldBe """
      plugins {
        id("com.rickbusarow.gradle-dependency-sync")
      }

      dependencies {
        dependencySync("com.android.tools.build:gradle:7.0.0")
      }
    """.trimIndent()

    tomlText() shouldBe """
      [versions]

      [libraries]
      androidGradlePlugin = "com.android.tools.build:gradle:7.0.0"
    """.trimIndent()
  }

  @Test
  fun `build file rc02 should be updated to stable`() = test(
    toml = """
      [versions]

      [libraries]
      androidGradlePlugin = "com.android.tools.build:gradle:7.0.0"
    """.trimIndent(),
    gradle = """
      plugins {
        id("com.rickbusarow.gradle-dependency-sync")
      }

      dependencies {
        dependencySync("com.android.tools.build:gradle:7.0.0-rc02")
      }
    """.trimIndent()
  ) {

    buildResult.shouldSucceed()

    buildResult.output shouldContain """
      updated build file dependency declaration
            old: com.android.tools.build:gradle:7.0.0-rc02
            new: com.android.tools.build:gradle:7.0.0
    """.trimIndent()

    buildText() shouldBe """
      plugins {
        id("com.rickbusarow.gradle-dependency-sync")
      }

      dependencies {
        dependencySync("com.android.tools.build:gradle:7.0.0")
      }
    """.trimIndent()

    tomlText() shouldBe """
      [versions]

      [libraries]
      androidGradlePlugin = "com.android.tools.build:gradle:7.0.0"
    """.trimIndent()
  }
}
