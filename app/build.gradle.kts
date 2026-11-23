import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

plugins {
    id("com.android.application")
}

// Magisk modules the Manager installs itself. Only fixes whose code we may ship:
// our own, or MIT with its LICENSE inside the module. Left out: 05 (needs your own
// logos), 07 (its .so is built by CI's fixes job, not here).
val bundledFixes = listOf(
    "03-devsettings-selinux",
    "09-status-led",
    "10-passthrough-tint",
    "11-charging-sound",
    "12-backlight",
    "13-wired-steamlink",
)

/** Zips each bundled fix's module/ into assets/modules/<fix>.zip, plus index.json (id, version, versionCode). */
abstract class BundleModules : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val modules: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun bundle() {
        val out = outputDir.get().asFile.resolve("modules")
        out.deleteRecursively()
        out.mkdirs()
        val entries = modules.files.sortedBy { it.parentFile.name }.map { dir ->
            val fix = dir.parentFile.name
            val prop = dir.resolve("module.prop").readLines()
                .mapNotNull { l ->
                    l.split('=', limit = 2).takeIf { it.size == 2 }
                        ?.let { it[0].trim() to it[1].trim() }
                }
                .toMap()
            ZipOutputStream(out.resolve("$fix.zip").outputStream()).use { zip ->
                dir.walkTopDown()
                    .filter { it.isFile && it.name != ".gitkeep" }
                    .sortedBy { it.relativeTo(dir).invariantSeparatorsPath }
                    .forEach { f ->
                        zip.putNextEntry(ZipEntry(f.relativeTo(dir).invariantSeparatorsPath).apply {
                            time = 315532800000L
                        })
                        f.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
            }
            fun q(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
            "  ${q(fix)}: {\"id\": ${q(prop.getValue("id"))}, \"version\": ${q(prop["version"] ?: "")}, " +
                    "\"versionCode\": ${prop["versionCode"]?.toIntOrNull() ?: 0}}"
        }
        out.resolve("index.json").writeText("{\n" + entries.joinToString(",\n") + "\n}\n")
    }
}

val bundleModules = tasks.register<BundleModules>("bundleModules") {
    modules.from(bundledFixes.map { rootProject.file("fixes/$it/module") })
    outputDir.set(layout.buildDirectory.dir("generated/qe-modules"))
}

android {
    namespace = "org.codeberg.anonymous950.questenhancer"

    compileSdk = 37
    defaultConfig {
        applicationId = "org.codeberg.anonymous950.questenhancer"
        minSdk = 27
        targetSdk = 37
        // CI passes its run number, so every build installs over the last one
        // and the file name, version code and run line up.
        versionCode = (project.findProperty("ciRun") as String?)?.toIntOrNull() ?: 3
        versionName = "1.1.0"
    }

    // A fixed key, so each CI build installs over the last one (the runner's
    // own debug key is new every run). Not a secret: it only signs this module.
    signingConfigs {
        getByName("debug") {
            storeFile = file("questenhancer-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        buildConfig = true
    }
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(bundleModules, BundleModules::outputDir)
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.kotlin.reflect)

    compileOnly(libs.xposed)
}
