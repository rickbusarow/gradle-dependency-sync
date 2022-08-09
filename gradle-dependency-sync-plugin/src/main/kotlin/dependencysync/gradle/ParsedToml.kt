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

import dependencysync.gradle.DependencySyncTask.Companion.complexReg
import dependencysync.gradle.DependencySyncTask.Companion.simpleReg
import dependencysync.gradle.DependencySyncTask.Companion.versionReg
import dependencysync.gradle.TomlEntry.Complex
import dependencysync.gradle.TomlEntry.Simple
import java.io.File

internal data class ParsedToml(
  val text: String,
  val librariesBlock: String,
  val versions: Map<String, TomlVersion>,
  val complexEntries: List<Complex>,
  val simpleEntries: List<Simple>,
  val entries: MutableList<TomlEntry>

) {
  internal companion object {

    val versionsBlockReg = """^[\s\S]*\[versions\]([\S\s]*)\[libraries\]""".toRegex()
    val librariesBlockReg = """\[libraries\]([^\[]*)""".toRegex()

    internal fun create(tomlFile: File): ParsedToml {
      val tomlText = tomlFile.readText()

      val tomlVersionBlock = versionsBlockReg.find(tomlText)?.destructured?.component1() ?: ""
      val tomlLibrariesBlock = librariesBlockReg.find(tomlText)?.destructured?.component1() ?: ""

      val tomlVersions = versionReg.findAll(tomlVersionBlock)
        .map { it.destructured }
        .map { (wholeString, name, version) ->
          TomlVersion(wholeString, name, version)
        }
        .associateBy { it.defName }

      val tomlComplexEntries = complexReg.findAll(tomlLibrariesBlock)
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

      val tomlSimpleEntries = simpleReg.findAll(tomlLibrariesBlock)
        .map { it.destructured }
        .map { (line, defName, group, name, version) ->
          TomlEntry.Simple(line, defName, Dep(group, name, version))
        }
        .toList()

      val tomlEntries = (tomlComplexEntries + tomlSimpleEntries).toMutableList()

      return ParsedToml(
        text = tomlText,
        librariesBlock = tomlLibrariesBlock,
        versions = tomlVersions,
        complexEntries = tomlComplexEntries,
        simpleEntries = tomlSimpleEntries,
        entries = tomlEntries
      )
    }
  }

  override fun toString(): String {
    return """ParsedToml(
    |  text='$text',
    |  librariesBlock='$librariesBlock',
    |  versions=$versions,
    |  complexEntries=$complexEntries,
    |  simpleEntries=$simpleEntries,
    |  entries=$entries
    |)
    """.trimMargin()
  }
}
