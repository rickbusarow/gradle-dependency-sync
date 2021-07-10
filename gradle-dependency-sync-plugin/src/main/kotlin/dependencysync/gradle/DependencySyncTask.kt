package dependencysync.gradle

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

    val versionReg = """((\S*)\s*=\s*"([^"]*)"\s*)""".toRegex()

    val simpleReg = """((\S*)\s*=\s*"([^":]*):([^":]*):([^"]*)")""".toRegex()
    val complexReg =
      """((\S*)\s*=\s*\{\s*module\s*=\s*"([^:]*):(\S*)"\s*,\s*version\.ref\s*=\s*"([^"]*)"\s*\}\s*)""".toRegex()
  }

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

        if (buildFileDep.version != tomlDep.version && entry is TomlEntry.Complex) {
          val newer = maxOf(buildFileDep.version, tomlDep.version)
          val older = minOf(buildFileDep.version, tomlDep.version)

          tomlEntries
            .filterIsInstance<TomlEntry.Complex>()
            .filter { it.versionDef == entry.versionDef }
            .map { it.dep.copy(version = older) to it.dep.copy(version = newer) }
            .forEach { (old, new) ->

              buildText = buildText.replace(old.toString(), new.toString())

              project.logger.quiet(
                """updated build file dependency declaration
                  |      old: $old
                  |      new: $new""".trimMargin()
              )
            }
        }

        if (buildFileDep.version > tomlDep.version) {
          val originalText = when (entry) {
            is TomlEntry.Complex -> entry.versionDef.originalText
            else -> entry.originalText
          }

          val newText = originalText.replace(tomlDep.version, buildFileDep.version)

          project.logger.quiet(
            """updated Toml dependency declaration
              |      old: $tomlDep
              |      new: $buildFileDep""".trimMargin()
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
