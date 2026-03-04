plugins {
    `java-library`
    `maven-publish`
    signing
}

group = "dev.libsql"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("org.graalvm.sdk:nativeimage:24.2.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<Test> {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

// --- Native library build ---

val libsqlDir = layout.projectDirectory.dir("libsql-c")
val nativeDir = layout.projectDirectory.dir("native")
val nativeResourceDir = layout.buildDirectory.dir("native-resources")

fun osArch(): String {
    val os = System.getProperty("os.name").lowercase().let {
        when {
            it.contains("mac") || it.contains("darwin") -> "darwin"
            it.contains("win") -> "windows"
            else -> "linux"
        }
    }
    val arch = System.getProperty("os.arch").let {
        when (it) {
            "aarch64" -> "aarch64"
            "amd64", "x86_64" -> "amd64"
            else -> it
        }
    }
    return "$os-$arch"
}

fun libName(): String {
    val os = System.getProperty("os.name").lowercase()
    return when {
        os.contains("mac") || os.contains("darwin") -> "liblibsql.dylib"
        os.contains("win") -> "libsql.dll"
        else -> "liblibsql.so"
    }
}

tasks.register<Exec>("initSubmodules") {
    description = "Initialize and update git submodules"
    onlyIf { !libsqlDir.dir(".git").asFile.exists() && !libsqlDir.file("Cargo.toml").asFile.exists() }
    commandLine("git", "submodule", "update", "--init", "--recursive")
}

tasks.register<Delete>("cleanNative") {
    description = "Remove native build outputs"
    delete(nativeDir)
}

tasks.register<Exec>("buildLibsqlNative") {
    description = "Build liblibsql native library from source"
    dependsOn("initSubmodules")
    val platform = osArch()
    workingDir(libsqlDir)
    commandLine("cargo", "build", "--release", "--features", "encryption")
    doLast {
        val targetLib = libsqlDir.dir("target/release").file(libName()).asFile
        val destDir = nativeDir.dir(platform).asFile
        destDir.mkdirs()
        targetLib.copyTo(destDir.resolve(libName()), overwrite = true)
    }
}

// Copy native libs into resources for JAR packaging
tasks.register<Copy>("copyNativeResources") {
    dependsOn("buildLibsqlNative")
    from(nativeDir)
    into(nativeResourceDir.map { it.dir("native") })
}

tasks.named("processResources") {
    dependsOn("copyNativeResources")
}

sourceSets {
    main {
        resources.srcDir(nativeResourceDir)
    }
}

// Ensure sourcesJar doesn't fail on missing native resources
tasks.named<Jar>("sourcesJar") {
    dependsOn("copyNativeResources")
}

// --- Publishing ---

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                name = "libsql-java"
                description = "Java FFM (Panama) bindings for libsql-c"
                url = "https://github.com/conorrestall/libsql-java"
                licenses {
                    license {
                        name = "MIT License"
                        url = "https://opensource.org/licenses/MIT"
                    }
                }
                developers {
                    developer {
                        id = "conorrestall"
                        name = "Conor Restall"
                    }
                }
                scm {
                    connection = "scm:git:git://github.com/conorrestall/libsql-java.git"
                    developerConnection = "scm:git:ssh://github.com/conorrestall/libsql-java.git"
                    url = "https://github.com/conorrestall/libsql-java"
                }
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/conorrestall/libsql-java")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

signing {
    isRequired = false
    sign(publishing.publications["mavenJava"])
}
