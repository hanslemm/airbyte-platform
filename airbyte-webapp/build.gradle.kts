import com.github.gradle.node.NodeExtension
import com.github.gradle.node.pnpm.task.PnpmTask
import groovy.json.JsonSlurper
import org.gradle.api.tasks.Copy
import java.io.FileReader

plugins {
    id("base")
    id("com.bmuschko.docker-remote-api")
    alias(libs.plugins.node.gradle)
}

// Use the node version that's defined in the .nvmrc file
val nodeVersion = file("${projectDir}/.nvmrc").readText().trim()

// Read pnpm version to use from package.json engines.pnpm entry
val parsedJson = JsonSlurper().parse(FileReader("${projectDir}/package.json")) as Map<*, *>  // Cast to Map
val engines = parsedJson["engines"] as? Map<*, *>  // Safely cast to Map if 'engines' exists
val pnpmVer = engines?.get("pnpm")?.toString()?.trim()  // Extract 'pnpm' as String and trim

/*
This array should contain a path to all configs that are common to most build tasks and
might affect them (i.e. if any of those files change we want to rerun most tasks)
*/
val commonConfigs = listOf(
    ".env",
    ".env.production",
    "package.json",
    "pnpm-lock.yaml",
    "tsconfig.json",
    ".prettierrc.js"
)

configure<NodeExtension> {
    download = true
    version = nodeVersion
    pnpmVersion = pnpmVer
    distBaseUrl = "https://nodejs.org/dist"
}

tasks.named("pnpmInstall") {
    /*
    Add patches folder to inputs of pnpmInstall task, since it has pnpm-lock.yml as an output
    thus wouldn't rerun in case a patch get changed
    */
    inputs.dir("patches")
}

// fileTree to watch node_modules, but exclude the .cache dir since that might have changes on every build
val nodeModules = fileTree("node_modules") {
    exclude(".cache")
}

/*
fileTree to watch the public dir but exclude the auto generated buildInfo.json. It's content is anyway a
content hash, depending on the other files.
*/
val publicDir = fileTree("public") {
    exclude("buildInfo.json")
}

tasks.register<PnpmTask>("pnpmBuild") {
    dependsOn(tasks.named("pnpmInstall"))


    environment.put("VERSION", rootProject.ext.get("version") as String)

    args = listOf("build")

    inputs.property("cloudEnv", System.getenv("WEBAPP_BUILD_CLOUD_ENV") ?: "")
    inputs.files(commonConfigs)
    inputs.files(nodeModules)
    inputs.files(publicDir)
    inputs.file(".eslintrc.js")
    inputs.file(".stylelintrc")
    inputs.file("orval.config.ts")
    inputs.file("vite.config.mts")
    inputs.file("index.html")
    inputs.dir("scripts")
    inputs.dir("src")

    outputs.dir("build/app")
}

tasks.register<PnpmTask>("test") {
    dependsOn(tasks.named("assemble"))

    args = listOf("run", "test:ci")
    inputs.files(commonConfigs)
    inputs.file("jest.config.ts")
    inputs.file("babel.config.js")
    inputs.dir("src")

    /*
    The test has no outputs, thus we always treat the outputs up to date
    as long as the inputs have not changed
    */
    outputs.upToDateWhen { true }
}

tasks.register<PnpmTask>("e2etest") {
    dependsOn(tasks.named("pnpmInstall"))

    /*
    If the cypressWebappKey property has been set from the outside (see tools/bin/e2e_test.sh)
    we'll record the cypress session, otherwise we're not recording
    */
    val recordCypress = project.hasProperty("cypressWebappKey") && project.property("cypressWebappKey") as Boolean
    if (recordCypress) {
        environment.put("CYPRESS_KEY", project.property("cypressWebappKey") as String)
        args = listOf("run", "cypress:ci:record")
    } else {
        args = listOf("run", "cypress:ci")
    }

    /*
    Mark the outputs as never up to date, to ensure we always run the tests.
    We want this because they are e2e tests and can depend on other factors e.g., external dependencies.
    */
    outputs.upToDateWhen { false }
}

tasks.register<PnpmTask>("cloudE2eTest") {
    dependsOn(tasks.named("pnpmInstall"))
    val recordCypress = project.hasProperty("cypressCloudWebappKey") && project.property("cypressCloudWebappKey") as Boolean
    if (recordCypress) {
        environment.put("CYPRESS_KEY", project.property("cypressCloudWebappKey") as String)
        args = listOf("run", "cloud-test:stage:record")
    } else {
        args = listOf("run", "cloud-test:stage")
    }

    /*
    Mark the outputs as never up to date, to ensure we always run the tests.
    We want this because they are e2e tests and can depend on other factors e.g., external dependencies.
    */
    outputs.upToDateWhen { false }
}

//tasks.register<PnpmTask>("validateLinks") {
//    dependsOn(tasks.named("pnpmInstall"))
//
//    args = listOf("run", "validate-links")
//
//    inputs.file("scripts/validate-links.ts")
//    inputs.file("src/core/utils/links.ts")
//
//    // Configure the up-to-date check to always run in CI environments
//    outputs.upToDateWhen {
//        System.getenv("CI") == null
//    }
//}


tasks.register<PnpmTask>("buildStorybook") {
    dependsOn(tasks.named("pnpmInstall"))

    args = listOf("run", "build:storybook")

    inputs.files(commonConfigs)
    inputs.files(nodeModules)
    inputs.files(publicDir)
    inputs.dir(".storybook")
    inputs.dir("src")

    outputs.dir("build/storybook")

    environment = mapOf(
        "NODE_OPTIONS" to "--max_old_space_size=8192"
    )
}

tasks.register<Copy>("copyBuildOutput") {
    dependsOn(tasks.named("copyDocker"), tasks.named("pnpmBuild"))

    from("${project.projectDir}/build/app")
    into("build/docker/bin/build")
}

tasks.register<Copy>("copyNginx") {
    dependsOn(tasks.named("copyDocker"))

    from("${project.projectDir}/nginx")
    into("build/docker/bin/nginx")
}

// Those tasks should be run as part of the "check" task
tasks.named("check") {
    dependsOn(/* tasks.named("validateLinks"), */ tasks.named("test"))
}

tasks.named("build") {
    dependsOn(tasks.named("buildStorybook"))
}

tasks.named("buildDockerImage") {
    dependsOn(tasks.named("copyDocker"), tasks.named("copyNginx"), tasks.named("copyBuildOutput"))
}

// Include some cloud-specific tasks only in the airbyte-platform-internal environment
if (file("${project.projectDir}/../../cloud/cloud-webapp/cloud-tasks.gradle").exists()) {
    apply(from = "${project.projectDir}/../../cloud/cloud-webapp/cloud-tasks.gradle")
}
