import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipInputStream

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.secrets)
}

android {
  namespace = "com.raf.garagedoor"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.raf.garagedoor"
    minSdk = 34
    targetSdk = 36
    versionCode = 1
    versionName = "0.1.0"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
}

// MAPS_API_KEY comes from the gitignored .env; .env.example supplies a placeholder so builds never fail.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

/**
 * Downloads the offline speech model for the "Open garage door" voice command (Vosk small English,
 * Apache 2.0) once, checks its SHA-256, and packages it as app assets. Kept out of git because it
 * is 41 MB; the zip is cached under the root .gradle folder so clean builds do not download again.
 */
abstract class FetchVoskModel : DefaultTask() {
  @get:Input abstract val url: Property<String>
  @get:Input abstract val sha256: Property<String>
  @get:Internal abstract val cacheFile: RegularFileProperty
  @get:OutputDirectory abstract val outputDir: DirectoryProperty

  @TaskAction
  fun fetch() {
    val zip = cacheFile.get().asFile
    if (!zip.exists() || digestOf(zip) != sha256.get()) download(zip)
    check(digestOf(zip) == sha256.get()) { "Vosk model checksum mismatch: ${zip.path}" }
    val modelDir = outputDir.get().dir(MODEL_ASSET_DIR).asFile
    modelDir.deleteRecursively()
    unzipWithoutTopFolder(zip, modelDir)
    modelDir.resolve("uuid").writeText(sha256.get())
  }

  private fun download(target: File) {
    target.parentFile.mkdirs()
    URI(url.get()).toURL().openStream().use { input -> target.outputStream().use { input.copyTo(it) } }
  }

  private fun digestOf(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input -> digest.update(input.readBytes()) }
    return digest.digest().joinToString("") { "%02x".format(it) }
  }

  private fun unzipWithoutTopFolder(zip: File, target: File) {
    ZipInputStream(zip.inputStream()).use { entries ->
      generateSequence { entries.nextEntry }.filterNot { it.isDirectory }.forEach { entry ->
        val file = target.resolve(entry.name.substringAfter('/')).canonicalFile
        check(file.path.startsWith(target.canonicalPath)) { "Unexpected path in model zip: ${entry.name}" }
        file.parentFile.mkdirs()
        file.outputStream().use { entries.copyTo(it) }
      }
    }
  }

  companion object {
    const val MODEL_ASSET_DIR = "vosk-model-en-us"
  }
}

val fetchVoskModel = tasks.register<FetchVoskModel>("fetchVoskModel") {
  url = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
  sha256 = "30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498"
  cacheFile = rootProject.layout.projectDirectory.file(".gradle/vosk/vosk-model-small-en-us-0.15.zip")
  outputDir = layout.buildDirectory.dir("generated/voskModel")
}

androidComponents {
  onVariants { variant -> variant.sources.assets?.addGeneratedSourceDirectory(fetchVoskModel, FetchVoskModel::outputDir) }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.browser)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.glance.appwidget)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.work.runtime)
  implementation(libs.jna) { artifact { type = "aar" } }
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.play.services)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.maps.compose)
  implementation(libs.okhttp)
  implementation(libs.play.services.location)
  implementation(libs.vosk.android)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.okhttp.mockwebserver)
  debugImplementation(libs.androidx.compose.ui.tooling)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.ext.junit)
}
