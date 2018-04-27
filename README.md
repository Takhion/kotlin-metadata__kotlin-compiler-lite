# kotlin-compiler-lite
[![bintray](https://img.shields.io/bintray/v/takhion/kotlin-metadata/kotlin-compiler-lite.svg?style=flat-square&label=bintray)](https://bintray.com/takhion/kotlin-metadata/kotlin-compiler-lite)
[![maven-central](https://img.shields.io/maven-central/v/me.eugeniomarletti.kotlin.metadata/kotlin-compiler-lite.svg?style=flat-square&label=maven-central)](https://mvnrepository.com/artifact/me.eugeniomarletti.kotlin.metadata/kotlin-compiler-lite)

Wraps [JetBrains/kotlin] to produce a subset of the [Kotlin] compiler to be used by [kotlin-metadata].

##### It's a standalone project because versioning is parallel (follows the compiler) and building it is quite heavy, especially for the CI.

## Features

+ all Kotlin/Java sources, even for external dependencies
+ every package relocated under `me.eugeniomarletti.kotlin.metadata.shadow.*` in both compiled classes and sources
+ ~~"full" version of all [Protocol Buffers], instead of the "lite" one used by default (see [Options > `optimize_for` > `LITE_RUNTIME`](https://developers.google.com/protocol-buffers/docs/proto#options))~~ _(temporarily disabled until fixed)_
+ `.proto` files `package`/`import` directives fixed to reflect their relative locations (allows inspection through [the IDE](https://plugins.jetbrains.com/plugin/8277-protobuf-support))

## Download
### This should _not_ be used directly, see [kotlin-metadata] instead!
```gradle
compile "me.eugeniomarletti.kotlin.metadata:kotlin-compiler-lite:$version"
```

## Random notes

+ since having different versions of the gradle wrapper often makes the CI run out of memory and fail, `gradlew` is a symlink to the one in the wrapped repository
+ doesn't modify any files on the original build, instead relies on runtime injection of modules through an 
[initialization script]
+ overrides are performed in [`kotlin.init.gradle`] and [`__injected/override/build.gradle.kts`]
+ the [top level build](build.gradle.kts) handles:
  + downloading external dependencies
  + starting inner build with init script
  + uploading generated artifacts

[kotlin-metadata]: https://github.com/Takhion/kotlin-metadata
[JetBrains/kotlin]: https://github.com/JetBrains/kotlin
[Kotlin]: https://kotlinlang.org/
[Protocol Buffers]: https://developers.google.com/protocol-buffers/
[initialization script]: https://docs.gradle.org/current/userguide/init_scripts.html
[`kotlin.init.gradle`]: kotlin.init.gradle
[`__injected/override/build.gradle.kts`]: __injected/override/build.gradle.kts
