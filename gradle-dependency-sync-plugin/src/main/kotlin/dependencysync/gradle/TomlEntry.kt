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

internal sealed class TomlEntry : Comparable<TomlEntry> {
  abstract val originalText: String
  abstract val defName: String
  abstract val dep: Dep

  data class Simple(
    override val originalText: String,
    override val defName: String,
    override val dep: Dep
  ) : TomlEntry() {
    override fun toString(): String = "$defName = \"$dep\""
  }

  data class Complex(
    override val originalText: String,
    override val defName: String,
    val versionDef: TomlVersion,
    override val dep: Dep
  ) : TomlEntry() {
    override fun toString(): String =
      "$defName = { module = \"${dep.group}:${dep.name}\", version.ref = \"${versionDef.defName}\" }"
  }

  override fun compareTo(other: TomlEntry): Int {
    return defName.compareTo(other.defName)
  }
}

internal data class Dep(val group: String, val name: String, val version: String) {
  override fun toString(): String {
    return "$group:$name:$version"
  }

  fun toSimpleToml(): TomlEntry.Simple {
    val nameSegmentList = name.split("-", ".")
    val groupSegmentList = group.split("-", ".")
      .filterNot { it in excludes }

    val joined = (groupSegmentList + nameSegmentList).removeAdjacent()

    val defName = joined
      .joinToString("-")
      .lowercase()

    return TomlEntry.Simple("---------------", defName, this)
  }

  private companion object {
    val excludes = setOf(
      "app",
      "com",
      "dev",
      "github",
      "gitlab",
      "io",
      "me",
      "net",
      "org"
    )
  }
}

internal data class TomlVersion(
  val originalText: String,
  val defName: String,
  val version: String
)

private fun <T : Any> Iterable<T>.removeAdjacent(): List<T> {
  var last: T? = null
  return mapNotNull {
    if (it == last) {
      null
    } else {
      last = it
      it
    }
  }
}
