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

package dependencysync.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import javax.inject.Inject

@Suppress("UnnecessaryAbstractClass") // for Gradle
public abstract class DependencySyncExtension @Inject constructor(objects: ObjectFactory) {

  public val gradleBuildFile: Property<String> = objects.property(String::class.java)
    .convention("./build.gradle.kts")

  public val typeSafeFile: Property<String> = objects.property(String::class.java)
    .convention("./gradle/libs.versions.toml")
}

public fun Project.dependencySync(config: DependencySyncExtension.() -> Unit) {
  extensions.configure(DependencySyncExtension::class, config)
}

public class DependencySyncPlugin : Plugin<Project> {

  override fun apply(target: Project) {
    val settings = target.extensions.create<DependencySyncExtension>(EXTENSION_NAME)

    target.configurations.create(CONFIGURATION_NAME)

    target.tasks.create<DependencySyncTask>(TASK_NAME, settings)
  }

  internal companion object {
    const val TASK_NAME: String = "dependencySync"
    const val CONFIGURATION_NAME: String = "dependencySync"
    const val EXTENSION_NAME: String = "dependencySync"
  }
}
