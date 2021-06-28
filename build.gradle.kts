import org.apache.tools.ant.filters.ReplaceTokens
import org.jetbrains.changelog.closure
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("org.jetbrains.changelog") version "0.4.0"
  id("org.jetbrains.intellij") version "0.4.21"
  id("org.jetbrains.kotlin.jvm") version "1.3.72"
}

// variables from gradle.properties file
val pluginVersion: String by project
val pluginSinceBuild: String by project
val pluginUntilBuild: String by project
val platformVersion: String by project
val localIdeDirectory: String by project

group = "io.snyk.intellij"
description = "Snyk Vulnerability Scanner"
version = pluginVersion

dependencies {
  implementation("org.jetbrains.kotlin:kotlin-stdlib")

  implementation("com.atlassian.commonmark:commonmark:0.15.2")
  implementation("com.google.code.gson:gson:2.8.6")
  implementation("com.segment.analytics.java:analytics:3.1.0")
  implementation("io.snyk.code.sdk:snyk-code-client:2.1.10")
  implementation("ly.iterative.itly:plugin-iteratively:1.2.7")
  implementation("ly.iterative.itly:plugin-schema-validator:1.2.7") {
    exclude(group = "org.slf4j")
  }
  implementation("ly.iterative.itly:sdk-jvm:1.2.7")

  testImplementation("junit:junit:4.13") {
    exclude(group = "org.hamcrest")
  }
  testImplementation("org.hamcrest:hamcrest:2.2")
  testImplementation("org.mockito:mockito-core:3.5.2")
}

intellij {
  version = platformVersion
}

repositories {
  mavenCentral()
}

tasks {
  withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
    kotlinOptions.languageVersion = "1.3"
  }

  withType<ProcessResources> {
    filesMatching("application.properties") {
      val segmentWriteKey = project.findProperty("segmentWriteKey") ?: ""
      val iterativelyEnvironment = project.findProject("iterativelyEnvironment") ?: "DEVELOPMENT"
      val tokens = mapOf(
        "segment.analytics.write-key" to segmentWriteKey,
        "iteratively.analytics.environment" to iterativelyEnvironment
      )
      filter<ReplaceTokens>("tokens" to tokens)
    }
  }

  buildSearchableOptions {
    enabled = false
  }

  patchPluginXml {
    version(pluginVersion)
    sinceBuild(pluginSinceBuild)
    untilBuild(pluginUntilBuild)

    pluginDescription(closure {
      File("$projectDir/README.md").readText().lines().run {
        val start = "<!-- Plugin description start -->"
        val end = "<!-- Plugin description end -->"

        if (!containsAll(listOf(start, end))) {
          throw GradleException("Plugin description section not found in README.md file:\n$start ... $end")
        }
        subList(indexOf(start) + 1, indexOf(end))
      }.joinToString("\n").run { markdownToHTML(this) }
    })

    changeNotes(
      closure {
        changelog.getLatest().toHTML()
      }
    )
  }

  publishPlugin {
    token(System.getenv("PUBLISH_TOKEN"))
    channels(pluginVersion.split('-').getOrElse(1) { "default" }.split('.').first())
  }

  runIde {
    maxHeapSize = "2g"
    if (localIdeDirectory.isNotEmpty()) {
      ideDirectory(localIdeDirectory)
    }
  }
}
