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

package dependencysync.gradle

import net.swiftzer.semver.SemVer
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File
import javax.inject.Inject

public open class DependencySyncTask @Inject constructor(
  @Input
  public val settings: DependencySyncExtension
) : DefaultTask() {

  internal companion object {

    const val SEMVER_SIZE = 3

    val versionReg = """((\S*)\s*=\s*"([^"]*)"\s*)""".toRegex()

    val simpleReg = """((\S*)\s*=\s*"([^":]*):([^":]*):([^"]*)")""".toRegex()
    val complexReg =
      """((\S*)\s*=\s*\{\s*module\s*=\s*"([^:]*):(\S*)"\s*,\s*version\.ref\s*=\s*"([^"]*)"\s*\}\s*)""".toRegex()
  }

  @Suppress("NestedBlockDepth", "LongMethod")
  @TaskAction
  public fun action() {
    val gradleBuildFile = File(settings.gradleBuildFile.get())
    val tomlFile = File(settings.typeSafeFile.get())

    var buildText = gradleBuildFile.readText()

    val dependencySyncConfig = project.configurations
      .getByName(DependencySyncPlugin.CONFIGURATION_NAME)

    val parsedBuildFile = ParsedBuildFile.create(dependencySyncConfig)

    val parsedToml = ParsedToml.create(tomlFile)

    var tomlText = parsedToml.text
    val tomlLibrariesBlock = parsedToml.librariesBlock
    val tomlEntries = parsedToml.entries

    buildText = addMissingBuildFileDeps(tomlEntries, parsedBuildFile, buildText)

    val updatedparsedBuildFile = ParsedBuildFile.create(dependencySyncConfig)

    val buildFileDeps = updatedparsedBuildFile.deps
    val groupedBuildFileDeps = updatedparsedBuildFile.groupedDeps

    val missingFromToml = buildFileDeps
      .filterNot { buildFileDep ->
        tomlEntries.any { it.dep.group == buildFileDep.group && it.dep.name == buildFileDep.name }
      }
      .map { it.toSimpleToml() }
      .onEach { newDep ->
        project.logger.quiet("added new Toml dependency declaration: \t\t ${newDep.dep}")
      }

    tomlEntries.addAll(missingFromToml)

    val grouped = tomlEntries.groupBy {
      it.dep
        .group
        .split(".")
        .takeIf { it.isNotEmpty() }
        ?.joinToString("-")
        ?: it.defName
    }

    val newToml = grouped.keys
      .sorted()
      .map { key ->
        buildString {
          grouped.getValue(key)
            .sorted()
            .forEach { appendLine(it) }
        }
      }
      .sorted()
      .joinToString("\n", "\n", "\n")

    tomlText = tomlText.replace(tomlLibrariesBlock, newToml)

    tomlEntries
      .filter {
        val tomlDep = it.dep

        val buildFileDep = groupedBuildFileDeps[tomlDep.group]?.get(tomlDep.name)

        buildFileDep != null
      }
      .forEach { entry ->

        val tomlDep = entry.dep

        val buildFileDep = groupedBuildFileDeps[tomlDep.group]
          ?.get(tomlDep.name)
          ?: return@forEach

        // Non-SemVer versions which have too many dots can't be parsed.
        // This turns "1.0.0.RELEASE" into "1.0.0".
        val tomlSplit = tomlDep.version
          .split(".")
          .take(SEMVER_SIZE)
          .joinToString(".")
        val buildFileSplit = buildFileDep.version
          .split(".")
          .take(SEMVER_SIZE)
          .joinToString(".")

        // comparisons need to be done as SemVer, because with regular String comparison,
        // a pre-release version like 1.0.0-rc02 is "greater" than the stable 1.0.0
        val buildSemVer = SemVer.parse(buildFileSplit)
        val tomlSemVer = SemVer.parse(tomlSplit)

        if (buildFileDep.version != tomlDep.version) {

          // SemVer can be used for the comparison, but their `.toString()` doesn't always work.
          // SemVer would change "1" to "1.0.0", or "2.36" to "2.36.0",
          // and those versions wouldn't resolve.
          val (newer, older) = if (buildSemVer > tomlSemVer) {
            buildFileDep.version to tomlDep.version
          } else {
            tomlDep.version to buildFileDep.version
          }

          if (newer != buildFileDep.version) {
            val new = buildFileDep.copy(version = newer)
            buildText = buildText.replace(buildFileDep.toString(), new.toString())

            project.logger.quiet(
              """updated build file dependency declaration
              |      old: $buildFileDep
              |      new: $new
              """.trimMargin()
            )
          }

          if (entry is TomlEntry.Complex) {
            tomlEntries
              .filterIsInstance<TomlEntry.Complex>()
              .filter { it.versionDef == entry.versionDef }
              .map { it.dep.copy(version = older) to it.dep.copy(version = newer) }
              .forEach { (old, new) ->

                // Non-SemVer versions don't work using regular strings for replacement token.
                // For example look at Dagger:
                // old deps
                //   ...dagger-compiler:2.39.1
                //   ...dagger:2.39
                // The `old.toString()` here is `2.39`, and replacing `old.toString()`
                // with `new.toString()` will replace it in both strings, resulting in:
                //   ...dagger-compiler:2.39.1.1
                //   ...dagger:2.39.1
                // Instead, use a regex with `^___$` for the token to be replaced, so that it only
                // replaces strings which are a perfect match.
                val oldRegex = "^${Regex.escapeReplacement(old.toString())}$".toRegex()

                buildText = buildText.replace(oldRegex, new.toString())

                project.logger.quiet(
                  """updated build file dependency declaration
                  |      old: $old
                  |      new: $new
                  """.trimMargin()
                )
              }
          }
        }

        if (buildSemVer > tomlSemVer) {
          val originalText = when (entry) {
            is TomlEntry.Complex -> entry.versionDef.originalText
            is TomlEntry.Simple -> entry.originalText
          }

          val newText = originalText.replace(tomlDep.version, buildFileDep.version)

          project.logger.quiet(
            """updated Toml dependency declaration
            |      old: $tomlDep
            |      new: $buildFileDep
            """.trimMargin()
          )

          tomlText = tomlText.replace(originalText, newText)
        }
      }

    gradleBuildFile.writeText(buildText)
    tomlFile.writeText(tomlText)
  }

  private fun addMissingBuildFileDeps(
    tomlEntries: MutableList<TomlEntry>,
    parsedBuildFile: ParsedBuildFile,
    originalBuildText: String
  ): String {
    var buildText = originalBuildText

    val groupedBuildFileDeps = parsedBuildFile.groupedDeps

    val (missingFromBuildFile, inBuildFile) = tomlEntries
      .map { it.dep }
      .partition { dep -> groupedBuildFileDeps[dep.group]?.get(dep.name) == null }

    val lastBuildDep = inBuildFile.lastOrNull()
      ?: throw GradleException("Cannot find any `dependencySync` dependencies in build file")

    val lastBuildDepRegex =
      """(.*dependencySync.*['"])${lastBuildDep.group}:${lastBuildDep.name}:.*(['"].*)""".toRegex()

    val lastBuildDepResult = buildText.lines()
      .asSequence()
      .mapNotNull { lastBuildDepRegex.find(it) }
      .firstOrNull()
      ?: throw GradleException(
        "Cannot find a dependency declaration for $lastBuildDep, " +
          "using the regex ${lastBuildDepRegex.pattern}, in build file"
      )

    val (prefix, suffix) = lastBuildDepResult.destructured

    missingFromBuildFile.forEach { missingDep ->
      val new = """$prefix$missingDep$suffix
          |$prefix$lastBuildDep$suffix
      """.trimMargin()
      buildText = buildText.replace(lastBuildDepResult.value, new)

      project.logger.quiet("added new build file dependency declaration: \t\t $missingDep")
    }
    return buildText
  }
}
