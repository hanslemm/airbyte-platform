import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
  id("io.airbyte.gradle.jvm.lib")
}

dependencies {
  annotationProcessor(libs.micronaut.openapi)

  ksp(libs.micronaut.openapi)
  ksp(platform(libs.micronaut.platform))
  ksp(libs.bundles.micronaut.annotation.processor)
  ksp(libs.v3.swagger.annotations)
  ksp(libs.jackson.kotlin)
  ksp(libs.moshi.kotlin)

  api(project(":oss:airbyte-api:commons"))
  api(project(":oss:airbyte-api:server-api"))

  implementation(platform(libs.micronaut.platform))
  implementation(libs.bundles.micronaut)
  implementation(libs.commons.io)
  implementation(libs.jakarta.annotation.api)
  implementation(libs.jakarta.ws.rs.api)
  implementation(libs.jakarta.validation.api)
  implementation(libs.jackson.datatype)
  implementation(libs.jackson.databind)
  implementation(libs.openapi.jackson.databind.nullable)
  implementation(libs.reactor.core)
  implementation(libs.slf4j.api)
  implementation(libs.swagger.annotations)
  implementation(project(":oss:airbyte-commons"))

  compileOnly(libs.v3.swagger.annotations)

  testRuntimeOnly(libs.junit.jupiter.engine)
  testImplementation(libs.bundles.junit)
  testImplementation(libs.bundles.jackson)
  testImplementation(libs.assertj.core)
  testImplementation(libs.junit.pioneer)
  testImplementation(libs.mockk)
  testImplementation(libs.kotlin.test.runner.junit5)
}

val connectorBuilderServerSpecFile = project(":oss:airbyte-connector-builder-server").file("src/main/openapi/openapi.yaml").path

val genConnectorBuilderServerApiClient = tasks.register<GenerateTask>("genConnectorBuilderServerApiClient") {
    val clientOutputDir = "${getLayout().buildDirectory.get()}/generated/connectorbuilderserverapi/client"

    inputs.file(connectorBuilderServerSpecFile)
    outputs.dir(clientOutputDir)

    generatorName = "kotlin"
    inputSpec = connectorBuilderServerSpecFile
    outputDir = clientOutputDir

    apiPackage = "io.airbyte.connectorbuilderserver.api.client.generated"
    invokerPackage = "io.airbyte.connectorbuilderserver.api.client.invoker.generated"
    modelPackage = "io.airbyte.connectorbuilderserver.api.client.model.generated"

    schemaMappings = mapOf(
            "ConnectorConfig"   to "com.fasterxml.jackson.databind.JsonNode",
            "ConnectorManifest" to "com.fasterxml.jackson.databind.JsonNode",
            "ConnectorBuilderProjectTestingValues" to "com.fasterxml.jackson.databind.JsonNode",
    )

    generateApiDocumentation = false

    configOptions = mapOf(
      "enumPropertyNaming"  to "UPPERCASE",
      "generatePom"         to "false",
      "interfaceOnly"       to "true",
      "serializationLibrary" to "jackson",
    )

    doLast {
        // Delete file generated by the client task
        delete(file("${outputDir.get()}/src/main/kotlin/org"))

        val generatedDomainClientsPath = "${outputDir.get()}/src/main/kotlin/io/airbyte/connectorbuilderserver/api/client/generated"
        updateDomainClientsWithFailsafe(generatedDomainClientsPath)
        // the kotlin client (as opposed to the java client) doesn't include the response body in the exception message.
        updateDomainClientsToIncludeHttpResponseBodyOnClientException(generatedDomainClientsPath)
    }

    dependsOn(":oss:airbyte-api:server-api:genApiClient")
}

sourceSets {
  main {
    kotlin {
      srcDirs(
        "${project.layout.buildDirectory.get()}/generated/connectorbuilderserverapi/client/src/main/kotlin",
        "$projectDir/src/main/kotlin",
      )
    }
    resources {
      srcDir("$projectDir/src/main/openapi/")
    }
  }
}

tasks.withType<JavaCompile>().configureEach {
  options.compilerArgs = listOf("-parameters")
}

tasks.named("compileKotlin") {
    dependsOn(genConnectorBuilderServerApiClient)
}

// uses afterEvaluate because at configuration time, the kspKotlin task does not exist.
afterEvaluate {
  tasks.named("kspKotlin").configure {
    mustRunAfter(genConnectorBuilderServerApiClient)
  }
}

// Even though Kotlin is excluded on Spotbugs, this project
// still runs into spotbug issues. Working theory is that
// generated code is being picked up. Disable as a short-term fix.
tasks.named("spotbugsMain") {
    enabled = false
}

private fun updateDomainClientsWithFailsafe(clientPath: String) {
  /*
   * UPDATE domain clients to use Failsafe.
   */
  val dir = file(clientPath)
  dir.walk().forEach { domainClient ->
    if (domainClient.name.endsWith(".kt")) {
      var domainClientFileText = domainClient.readText()

      // replace class declaration
      domainClientFileText = domainClientFileText.replace(
        "class (\\S+)\\(basePath: kotlin.String = defaultBasePath, client: OkHttpClient = ApiClient.defaultClient\\) : ApiClient\\(basePath, client\\)".toRegex(),
        "class $1(basePath: kotlin.String = defaultBasePath, client: OkHttpClient = ApiClient.defaultClient, policy : RetryPolicy<okhttp3.Response> = RetryPolicy.ofDefaults()) : ApiClient(basePath, client, policy)"
      )

      // add imports if not exist
      if(!domainClientFileText.contains("import dev.failsafe.RetryPolicy")) {
        val newImports = "import dev.failsafe.RetryPolicy"
        domainClientFileText = domainClientFileText.replaceFirst("import ", "$newImports\nimport ")
      }

      domainClient.writeText(domainClientFileText)
    }
  }
}

private fun updateDomainClientsToIncludeHttpResponseBodyOnClientException(clientPath: String) {
    val dir = file(clientPath)
    dir.walk().forEach { domainClient ->
        if (domainClient.name.endsWith(".kt")) {
            val domainClientFileText = domainClient.readText().replace(
                    "throw ClientException(\"Client error : \${localVarError.statusCode} \${localVarError.message.orEmpty()}\", localVarError.statusCode, localVarResponse)",
                    "throw ClientException(\"Client error : \${localVarError.statusCode} \${localVarError.message.orEmpty()} \${localVarError.body ?: \"\"}\", localVarError.statusCode, localVarResponse)")

            domainClient.writeText(domainClientFileText)
        }
    }
}
