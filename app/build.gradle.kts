import org.gradle.api.tasks.PathSensitivity

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.Atom2Universe.app"
    compileSdk = 36

    buildFeatures {
        buildConfig = true
        prefab = true  // Enable prefab for native dependencies (FluidSynth)
    }


    defaultConfig {
        applicationId = "com.Atom2Universe.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 3

        versionName = "0.1.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Déclarer explicitement toutes les langues supportées pour l'AAB
        resourceConfigurations += listOf(
            "en",  // Anglais (défaut)

            "fr",  // Français
            "el",  // Grec
            "de",  // Allemand
            "es",  // Espagnol
            "it",  // Italien
            "nl",  // Néerlandais
            "pl",  // Polonais
            "pt",  // Portugais
            "ro",  // Roumain
            "ru",  // Russe
            "tr",  // Turc
            "uk",  // Ukrainien
            "in"   // Indonésien (values-in)
        )

        // arm64 uniquement pour réduire la taille de l'APK
        // ChromeOS (x86_64) n'est pas ciblé
        ndk {
            abiFilters += "arm64-v8a"
        }

        // Configure CMake for FluidSynth JNI wrapper
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += "-DANDROID_STL=c++_shared"
            }
        }
    }

    // CMake build configuration
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    lint {
        disable += "ChromeOsAbiSupport"
    }

    buildTypes {
        debug {
            // Suffixe pour avoir debug et release en parallèle
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    // Résout les conflits de librairies natives entre mididriver, ffmpeg-kit et fluidsynth
    packaging {
        jniLibs {
            pickFirsts += listOf(
                "lib/arm64-v8a/libc++_shared.so",
                "lib/arm64-v8a/liboboe.so"
            )
        }
        resources {
            excludes += listOf(
                "META-INF/INDEX.LIST",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt"
            )
        }
    }

    // Force l'inclusion de TOUTES les langues dans l'AAB
    // (désactive les splits par langue pour les sélecteurs in-app)
    bundle {
        language {
            enableSplit = false
        }
    }
}

/**
 * Les fichiers de chaines comptent comme entrees des tests unitaires.
 *
 * `GearPanelFormatTest` lit `strings_trebuchet.xml` **sur le disque** pour essayer chaque
 * format avec l'argument qu'il recevra a l'ecran : c'est le seul moyen d'attraper un
 * `%d` nourri par un `Float`, que rien dans le Kotlin ne peut voir. Mais Gradle ne
 * devine pas cette lecture — pour lui, une tache de test ne depend que des classes — et
 * il declarait donc la tache a jour quand seul le XML avait bouge. Le garde-fou restait
 * vert sur une chaine deja cassee, ce qui est pire que pas de garde-fou du tout.
 */
tasks.withType<Test>().configureEach {
    inputs.files(
        fileTree("src/main/res") { include("values*/strings*.xml") }
    ).withPropertyName("stringResources").withPathSensitivity(PathSensitivity.RELATIVE)
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Layout & UI
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.gridlayout)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.viewpager2)
    implementation(libs.flexbox)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)

    // Room Database (MIDI & Music library)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Media & Audio
    implementation(libs.androidx.media)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)

    // Network & Async
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)

    // File access
    implementation(libs.androidx.documentfile)

    // MIDI Driver (Sonivox EAS synthesis) - local module
    implementation(project(":mididriver"))

    // SF2 SoundFont support via FluidSynth
    implementation(libs.fluidsynth)

    // MIDI Parser (android-midi-lib)
    implementation(libs.android.midi.lib)

    // JAudioTagger for ID3 tag editing - local module
    implementation(project(":jaudiotagger"))

    // FFmpeg-kit for audio processing (Audio Editor)
    implementation(libs.ffmpeg.kit)

    // Cloud Sync - Google Sign-In
    implementation(libs.google.play.services.auth)
    implementation(libs.kotlinx.coroutines.play.services)

    // Google Play Billing (Premium)
    implementation(libs.google.billing)

    // Cloud Sync - Google Drive API
    implementation(libs.google.api.client.android)
    implementation(libs.google.api.services.drive)

    // WorkManager for background sync
    implementation(libs.androidx.work.runtime.ktx)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
