package dependencysync.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File
import javax.inject.Inject

public open class DependencySyncTask @Inject constructor(
  @Input
  public  val settings: DependencySyncExtension
) : DefaultTask() {

  @TaskAction
  public fun action() {

    val gradleBuildFile = File(settings.gradleBuildFile.get())
    val tomlFile = File(settings.typeSafeFile.get())

    var buildText = gradleBuildFile.readText()

    val versionsBlockReg = """^[\s\S]*\[versions\]([\S\s]*)\[libraries\]""".toRegex()
    val librariesBlockReg = """^[\s\S]*\[libraries\]([\S\s]*)\[\S*\]""".toRegex()
    val versionReg = """((\S*)\s*=\s*"([^"]*)"\s*)""".toRegex()
    val simpleReg = """((\S*)\s*=\s*"([^":]*):([^":]*):([^"]*)")""".toRegex()
    val complexReg =
      """((\S*)\s*=\s*\{\s*module\s*=\s*"([^:]*):(\S*)"\s*,\s*version\.ref\s*=\s*"([^"]*)"\s*\}\s*)""".toRegex()

    val dummyConfig = project.configurations.getByName(DependencySyncPlugin.CONFIGURATION_NAME)

    val buildFileDeps = dummyConfig.dependencies
      .filter { it.version != null }
      .map { Dep(it.group!!, it.name, it.version!!) }
      .groupBy { it.group + it.name }
      .values
      .map { depsByArtifact -> depsByArtifact.maxByOrNull { it.version }!! }

    val thisFileDeps = buildFileDeps
      .groupBy { it.group }
      .map { (group, lst) ->
        group to lst.associateBy { it.name }
      }
      .toMap()

    var tomlText = tomlFile.readText()

    val versionBlock = versionsBlockReg.find(tomlText)?.destructured?.component1() ?: ""
    val librariesBlock = librariesBlockReg.find(tomlText)?.destructured?.component1() ?: ""

    val tomlVersions = versionReg.findAll(versionBlock)
      .map { it.destructured }
      .map { (wholeString, name, version) ->
        TomlVersion(wholeString, name, version)
      }
      .associateBy { it.defName }

    val complexEntries = complexReg.findAll(librariesBlock)
      .toList()
      .map { it.destructured }
      .map { (line, defName, group, name, versionRef) ->
        val versionDef = tomlVersions.getValue(versionRef)
        val version = versionDef.version

        TomlEntry.Complex(
          originalText = line,
          defName = defName,
          versionDef = versionDef,
          dep = Dep(group, name, version)
        )
      }

    val simpleEntries = simpleReg.findAll(librariesBlock)
      .map { it.destructured }
      .map { (line, defName, group, name, version) ->
        TomlEntry.Simple(line, defName, Dep(group, name, version))
      }
      .toList()

    val tomlEntries = (complexEntries + simpleEntries).toMutableList()

    val leftovers = buildFileDeps
      .filterNot { buildFileDep ->
        tomlEntries.any { it.dep.group == buildFileDep.group && it.dep.name == buildFileDep.name }
      }
      .map { it.toSimpleToml() }

    tomlEntries.addAll(leftovers)

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

    tomlText = tomlText.replace(librariesBlock, newToml)

    tomlEntries
      .forEach { entry ->

        val tomlDep = entry.dep

        val ktsDep = thisFileDeps[tomlDep.group]
          ?.get(tomlDep.name)
          ?: throw GradleException("Dependency `$tomlDep` is declared in $tomlFile but not in $gradleBuildFile")

        if (ktsDep.version != tomlDep.version && entry is TomlEntry.Complex) {

          val newer = maxOf(ktsDep.version, tomlDep.version)
          val older = minOf(ktsDep.version, tomlDep.version)

          tomlEntries
            .filterIsInstance<TomlEntry.Complex>()
            .filter { it.versionDef == entry.versionDef }
            .map { it.dep.copy(version = older) to it.dep.copy(version = newer) }
            .forEach { (old, new) ->

              buildText = buildText.replace(old.toString(), new.toString())
            }
        }

        if (ktsDep.version > tomlDep.version) {

          val originalText = when (entry) {
            is TomlEntry.Complex -> entry.versionDef.originalText
            else -> entry.originalText
          }

          val newText = originalText.replace(tomlDep.version, ktsDep.version)

          project.logger.quiet(
            """updated Toml dependency declaration
          |      old: $tomlDep
          |      new: $ktsDep""".trimMargin()
          )

          tomlText = tomlText.replace(originalText, newText)
        }
      }

    gradleBuildFile.writeText(buildText)
    tomlFile.writeText(tomlText)
  }
}
