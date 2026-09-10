import app.morphe.patches.gradle.PatchesExtension
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

apply<app.morphe.patches.gradle.PatchesPlugin>()

group = "app.morphe"
version = "0.3.0-local"

extensions.configure<PatchesExtension>("patches") {
    about {
        name = "YouTube Music Android Auto browse lab"
        description = "Experimental signed-in library, playlist contents, history and Home browsing for YouTube Music 9.15.51"
        source = "https://github.com/jimmy-s-dev/morphe-patches"
        author = "Local fork contributors; based on zappybiby's PR #2489"
        contact = "na"
        website = "https://github.com/jimmy-s-dev/morphe-patches"
        license = "GNU General Public License v3.0, with additional GPL section 7 requirements"
    }
}

dependencies {
    add("implementation", libs.guava)
    add("implementation", libs.morphe.patches.library)
    add("compileOnly", project(":patches:stub"))
    add("testImplementation", "app.morphe:morphe-patcher:${libs.versions.morphe.patcher.get()}")
    add("testImplementation", "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}

extensions.configure<KotlinJvmProjectExtension>("kotlin") {
    compilerOptions {
        freeCompilerArgs.set(listOf("-Xcontext-parameters"))
    }
    sourceSets {
        named("main") {
            kotlin.srcDir("../patches/src/main/kotlin")
            kotlin.include(
                "app/morphe/patches/music/misc/androidauto/playlists/Fingerprints.kt",
                "app/morphe/patches/music/misc/androidauto/playlists/RestoreAndroidAutoPlaylistsPatch.kt",
                "app/morphe/patches/music/shared/Constants.kt",
            )
        }
    }
}

tasks.withType<Jar>().configureEach {
    archiveBaseName.set("restore-android-auto-playlists")
    exclude(
        "extensions/music.mpe",
        "extensions/reddit.mpe",
        "extensions/shared.mpe",
        "extensions/shared-youtube.mpe",
        "extensions/youtube.mpe",
    )
}

tasks.named("build") {
    dependsOn("buildAndroid")
}

val sourceSets = extensions.getByType<SourceSetContainer>()
val compileBrowseBridgeTest = tasks.register<JavaCompile>("compileBrowseBridgeTest") {
    source(fileTree("src/browseTest/java") { include("**/*.java") })
    source(rootProject.file("extensions/music/src/main/java/app/morphe/extension/music/patches/RestoreAndroidAutoPlaylistsPatch.java"))
    source(rootProject.file("extensions/music/src/main/java/app/morphe/extension/music/patches/MusicHomeRenderer.java"))
    source(rootProject.file("extensions/youtube/stub/src/main/java/com/google/common/util/concurrent/ListenableFuture.java"))
    classpath = files()
    options.release.set(17)
    options.encoding = "UTF-8"
    destinationDirectory.set(layout.buildDirectory.dir("browse-test"))
}
tasks.register<JavaExec>("verifyBrowseBridge") {
    dependsOn(compileBrowseBridgeTest)
    classpath = files(compileBrowseBridgeTest.flatMap { it.destinationDirectory })
    mainClass.set("BrowseBridgeTest")
}
tasks.register<JavaExec>("applyToApk") {
    description = "Applies the experimental bundle to a copy of an APK without installing it"
    dependsOn("testClasses", "buildAndroid")
    classpath = sourceSets["test"].output + configurations["testRuntimeClasspath"]
    mainClass.set("ApplyAutoPatchKt")
    maxHeapSize = "4g"
    doFirst {
        // Shared extension factories are supplied by patches-library on the parent loader.
        // Make bundle resources visible there as well as to the patch loader.
        providers.gradleProperty("patchBundle").orNull?.let { classpath += files(it) }
        args(
            providers.gradleProperty("patchBundle").get(),
            providers.gradleProperty("inputApk").get(),
            providers.gradleProperty("outputApk").get(),
        )
    }
}
