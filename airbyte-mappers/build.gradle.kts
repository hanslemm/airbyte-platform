plugins {
  id("io.airbyte.gradle.jvm.lib")
  id("io.airbyte.gradle.publish")
}

dependencies {
  ksp(platform(libs.micronaut.platform))
  ksp(libs.bundles.micronaut.annotation.processor)

  implementation(platform(libs.micronaut.platform))
  implementation(libs.bundles.micronaut)

  implementation(project(":oss:airbyte-commons"))
  implementation(project(":oss:airbyte-config:config-models"))

  implementation(libs.bundles.jackson)
  implementation(libs.kotlin.logging)

  testImplementation(project(":oss:airbyte-commons"))
  testImplementation(libs.mockito.core)
  testImplementation(libs.mockk)
}
