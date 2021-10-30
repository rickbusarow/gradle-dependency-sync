## What does it do?

This Gradle plugin will sync dependency declarations between
a `build.gradle` or `build.gradle.kts` file and
[Gradle's new .toml file.](https://docs.gradle.org/nightly/userguide/platforms.html#sub:central-declaration-of-dependencies)

This is useful if you'd like to utilize IntelliJ/Android Studio's built in dependency update warnings,
or if you're using [Dependabot](https://github.com/dependabot/dependabot-core).  Both of these tools only support
dependencies which are defined as string literals in a Gradle build file.

If a dependency is declared in the build file but not in .toml,
DependencySync will automatically create a new declaration in the toml file.

If the dependency is declared in both files, but with different versions,
DependencySync will replace the older version with the newer one.

If a toml dependency is declared using a version from the `[versions]` section via `version.ref = "my-dependency-version"`,
then DependencySync will update the declaration for `my-dependency-version` instead.
It will then look for any dependencies in the build file which may need to be updated in order to keep both files in sync.

## Config

First, create an empty module in your project, such as `:dependency-sync`.  Add this to your root project's `settings.gradle[.kts]`
Do not add this module as a dependency to any other module.

```kotlin
// settings.gradle.kts

pluginManagement {
  repositories {
    gradlePluginPortal()
  }
}

...

include(":dependency-sync")
```
Now add a `build.gradle[.kts]` file.  Declare every single external dependency for the entire project here,
using the `dependencySync` configuration.  This configuration doesn't do anything,
but it's necessary for the IDE and Dependabot to do their parsing.

```kotlin
// ./dependency-sync/build.gradle.kts

plugins {
  id("com.rickbusarow.gradle-dependency-sync") version "0.11.4"
}

dependencySync {
  gradleBuildFile.set(buildFile.path)  // optional -- default is just the build file of the applied module
  typeSafeFile.set("${rootDir}/gradle/libs.versions.toml") // optional -- this path is the default
}

dependencies {

  dependencySync("com.android.tools.build:gradle:4.2.1")

  dependencySync("androidx.activity:activity-ktx:1.2.3")

  // ...
}
```

## Task

This task is very fast.  You can obviously just invoke it manually, but you can also try invoking it as part of your CI on Dependabot pull requests.

``` shell
./gradlew dependencySync
```

## License

``` text
Copyright (C) 2021 Rick Busarow
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
     http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
