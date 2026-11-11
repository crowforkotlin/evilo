import org.gradle.api.tasks.Copy
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension
import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsRootExtension
import org.jetbrains.kotlin.gradle.targets.web.nodejs.BaseNodeJsRootExtension

plugins { id("evilo.web.application") }

kotlin {
    sourceSets.commonMain.dependencies { implementation(projects.eviloApp) }
    sourceSets.named("webMain") {
        dependencies { implementation(npm("@sqlite.org/sqlite-wasm", libs.versions.sqlite.wasm.get())) }
    }
}

fun sqliteRuntime(target: String, node: BaseNodeJsRootExtension) = tasks.register<Copy>("${target}CopySqliteRuntime") {
    val runtime = node.rootPackageDirectory.map { it.dir("node_modules/@sqlite.org/sqlite-wasm/dist") }
    dependsOn(node.npmInstallTaskProvider)
    from(runtime) {
        include("index.mjs", "sqlite3.wasm", "sqlite3-opfs-async-proxy.js")
        into("sqlite-worker/sqlite3")
    }
    into(layout.buildDirectory.dir("generated/sqliteRuntime/$target"))
    doFirst {
        check(runtime.get().file("index.mjs").asFile.isFile && runtime.get().file("sqlite3.wasm").asFile.isFile) {
            "The installed @sqlite.org/sqlite-wasm package does not contain its required dist/ assets."
        }
    }
}

val jsSqliteRuntime = sqliteRuntime("js", rootProject.extensions.getByType<NodeJsRootExtension>())
val wasmSqliteRuntime = sqliteRuntime("wasmJs", rootProject.extensions.getByType<WasmNodeJsRootExtension>())
kotlin.sourceSets.named("jsMain") { resources.srcDir(jsSqliteRuntime) }
kotlin.sourceSets.named("wasmJsMain") { resources.srcDir(wasmSqliteRuntime) }
tasks.matching { it.name == "jsProcessResources" }.configureEach { dependsOn(jsSqliteRuntime) }
tasks.matching { it.name == "wasmJsProcessResources" }.configureEach { dependsOn(wasmSqliteRuntime) }
