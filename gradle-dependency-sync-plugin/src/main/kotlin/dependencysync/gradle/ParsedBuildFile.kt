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

import org.gradle.api.artifacts.Configuration

internal data class ParsedBuildFile(
  val deps: List<Dep>,
  val groupedDeps: Map<String, Map<String, Dep>>
) {
  internal companion object {

    internal fun create(dependencySyncConfig: Configuration): ParsedBuildFile {

      val buildFileDeps = dependencySyncConfig.dependencies
        .mapNotNull { dependency ->
          val group = dependency.group ?: return@mapNotNull null
          val version = dependency.version ?: return@mapNotNull null
          Dep(group = group, name = dependency.name, version = version)
        }
        .groupBy { it.group + it.name }
        .values
        .mapNotNull { depsByArtifact -> depsByArtifact.maxByOrNull { it.version } }

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
