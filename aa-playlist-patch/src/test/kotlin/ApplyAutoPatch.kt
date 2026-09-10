import app.morphe.patcher.Patcher
import app.morphe.patcher.PatcherConfig
import app.morphe.patcher.apk.ApkUtils.applyTo
import app.morphe.patcher.dex.BytecodeMode
import app.morphe.patcher.patch.loadPatchesFromJar
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files

fun main(args: Array<String>) = runBlocking {
    require(args.size == 3) { "Expected bundle, input APK and new output APK" }
    val input = File(args[1]).canonicalFile
    val output = File(args[2]).canonicalFile
    require(input.isFile && !output.exists() && input != output)
    output.parentFile.mkdirs()
    // The patcher clears its temporary directory; give it an exclusively owned new directory.
    val temporary = Files.createTempDirectory(output.parentFile.toPath(), "aa-patcher-").toFile()
    Patcher(PatcherConfig(input, temporary, useBytecodeMode = BytecodeMode.FULL)).use { patcher ->
        require(patcher.context.packageMetadata.packageName == "com.google.android.apps.youtube.music" &&
            patcher.context.packageMetadata.versionName == "9.15.51") {
            "This lab runner requires the original YouTube Music 9.15.51 APK"
        }
        val patches = loadPatchesFromJar(setOf(File(args[0])))
        if (patches.size == 1) {
            error("Use the full patches bundle: the lab runner requires GmsCore, branding and Clone app")
        } else {
            val music = patches.filter { patch ->
                patch.compatibility?.any { it.packageName == "com.google.android.apps.youtube.music" } == true
            }
            val names = setOf("GmsCore support", "Bypass certificate checks", "Custom branding",
                "Remove background playback restrictions", "Restore playlists in Android Auto")
            val selected = names.map { name -> music.single { it.name == name } }.toMutableSet()
            val clone = patches.single { it.name == "Clone app" }
            clone.options["packageName"] = "app.morphe.android.apps.youtube.music.aalab"
            clone.options["updatePermissions"] = true
            clone.options["updateProviders"] = true
            selected.add(clone)
            selected.single { it.name == "Custom branding" }.options["customName"] = "Music Auto Lab"
            println("Selected: ${selected.map { it.name }}")
            patcher += selected
        }
        patcher().collect { result ->
            result.exception?.let { throw it }
            println("Applied: ${result.patch.name}")
        }
        val result = patcher.get()
        input.copyTo(output, overwrite = false)
        result.applyTo(output)
        println("Unsigned output: $output")
    }
}
