// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.roborazzi) apply false
  alias(libs.plugins.secrets) apply false
}

import java.util.Base64
import java.io.File

tasks.register("generatePersistentKeystore") {
  doLast {
    val keystoreFile = file("debug.keystore")
    val base64File = file("debug.keystore.base64")
    
    if (keystoreFile.exists()) {
      keystoreFile.delete()
    }
    
    val process = ProcessBuilder(
      "keytool",
      "-genkey",
      "-v",
      "-keystore", keystoreFile.absolutePath,
      "-storepass", "android",
      "-alias", "androiddebugkey",
      "-keypass", "android",
      "-keyalg", "RSA",
      "-keysize", "2048",
      "-validity", "10000",
      "-dname", "cn=Unknown, ou=Unknown, o=Unknown, c=Unknown",
      "-storetype", "PKCS12"
    ).inheritIO().start()
    
    val exitCode = process.waitFor()
    if (exitCode != 0) {
      throw GradleException("Failed to run keytool: exit code $exitCode")
    }
    
    if (keystoreFile.exists()) {
      println("Keystore generated successfully at: ${keystoreFile.absolutePath}")
      val bytes = keystoreFile.readBytes()
      val base64String = Base64.getEncoder().encodeToString(bytes)
      base64File.writeText(base64String)
      println("Base64 representation written successfully to: ${base64File.absolutePath}")
    } else {
      throw GradleException("Failed to generate keystore file!")
    }
  }
}
