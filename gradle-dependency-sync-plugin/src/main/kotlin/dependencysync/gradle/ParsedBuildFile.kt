package dependencysync.gradle

import org.gradle.api.artifacts.Configuration

internal data class ParsedBuildFile(
  val deps: List<Dep>,
  val groupedDeps: Map<String, Map<String, Dep>>
) {
  internal companion object {

    internal fun create(
      dependencySyncConfig: Configuration
    ): ParsedBuildFile {
      val buildFileDeps = dependencySyncConfig.dependencies
        .filter { it.version != null }
        .map { Dep(it.group!!, it.name, it.version!!) }
        .groupBy { it.group + it.name }
        .values
        .map { depsByArtifact -> depsByArtifact.maxByOrNull { it.version }!! }

      val groupedBuildFileDeps = buildFileDeps
        .groupBy { it.group }
        .map { (group, lst) ->
          group to lst.associateBy { it.name }
        }
        .toMap()
      return ParsedBuildFile(
        deps = buildFileDeps,
        groupedDeps = groupedBuildFileDeps
      )
    }
  }
}
