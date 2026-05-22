////////////////////////////////////////////////////////////////////////////////
//
//  FluidSynth JNI Wrapper for KindEars
//
//  Copyright (C) 2024 KindEars Project
//
//  This wrapper provides JNI bindings to the FluidSynth library for Android.
//  Uses dlopen/dlsym to dynamically load libfluidsynth.so at runtime.
//  FluidSynth itself is licensed under LGPL 2.1.
//
////////////////////////////////////////////////////////////////////////////////

#include <jni.h>
#include <string>
#include <android/log.h>
#include <dlfcn.h>
#include <fluidsynth.h>

#define LOG_TAG "FluidSynthJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ==================== Function Pointer Types ====================

typedef fluid_settings_t* (*fn_new_fluid_settings)(void);
typedef void (*fn_delete_fluid_settings)(fluid_settings_t*);
typedef int (*fn_fluid_settings_setstr)(fluid_settings_t*, const char*, const char*);
typedef int (*fn_fluid_settings_setnum)(fluid_settings_t*, const char*, double);
typedef int (*fn_fluid_settings_setint)(fluid_settings_t*, const char*, int);

typedef fluid_synth_t* (*fn_new_fluid_synth)(fluid_settings_t*);
typedef void (*fn_delete_fluid_synth)(fluid_synth_t*);
typedef int (*fn_fluid_synth_sfload)(fluid_synth_t*, const char*, int);
typedef int (*fn_fluid_synth_sfunload)(fluid_synth_t*, int, int);
typedef int (*fn_fluid_synth_noteon)(fluid_synth_t*, int, int, int);
typedef int (*fn_fluid_synth_noteoff)(fluid_synth_t*, int, int);
typedef int (*fn_fluid_synth_program_change)(fluid_synth_t*, int, int);
typedef int (*fn_fluid_synth_bank_select)(fluid_synth_t*, int, int);
typedef int (*fn_fluid_synth_cc)(fluid_synth_t*, int, int, int);
typedef int (*fn_fluid_synth_pitch_bend)(fluid_synth_t*, int, int);
typedef int (*fn_fluid_synth_all_notes_off)(fluid_synth_t*, int);
typedef int (*fn_fluid_synth_all_sounds_off)(fluid_synth_t*, int);
typedef int (*fn_fluid_synth_system_reset)(fluid_synth_t*);
typedef void (*fn_fluid_synth_set_gain)(fluid_synth_t*, float);
typedef float (*fn_fluid_synth_get_gain)(fluid_synth_t*);
typedef int (*fn_fluid_synth_get_polyphony)(fluid_synth_t*);
typedef int (*fn_fluid_synth_get_active_voice_count)(fluid_synth_t*);
typedef int (*fn_fluid_synth_set_reverb_on)(fluid_synth_t*, int);
typedef int (*fn_fluid_synth_reverb_on)(fluid_synth_t*, int, int);
typedef int (*fn_fluid_synth_set_reverb)(fluid_synth_t*, double, double, double, double);
typedef int (*fn_fluid_synth_set_chorus_on)(fluid_synth_t*, int);
typedef int (*fn_fluid_synth_chorus_on)(fluid_synth_t*, int, int);
typedef int (*fn_fluid_synth_write_s16)(fluid_synth_t*, int, void*, int, int, void*, int, int);
typedef int (*fn_fluid_synth_write_float)(fluid_synth_t*, int, void*, int, int, void*, int, int);

typedef fluid_audio_driver_t* (*fn_new_fluid_audio_driver)(fluid_settings_t*, fluid_synth_t*);
typedef void (*fn_delete_fluid_audio_driver)(fluid_audio_driver_t*);

typedef fluid_player_t* (*fn_new_fluid_player)(fluid_synth_t*);
typedef void (*fn_delete_fluid_player)(fluid_player_t*);
typedef int (*fn_fluid_player_add)(fluid_player_t*, const char*);
typedef int (*fn_fluid_player_play)(fluid_player_t*);
typedef int (*fn_fluid_player_stop)(fluid_player_t*);
typedef int (*fn_fluid_player_get_status)(fluid_player_t*);
typedef int (*fn_fluid_player_seek)(fluid_player_t*, int);
typedef int (*fn_fluid_player_get_current_tick)(fluid_player_t*);
typedef int (*fn_fluid_player_get_total_ticks)(fluid_player_t*);
typedef int (*fn_fluid_player_set_loop)(fluid_player_t*, int);
typedef int (*fn_fluid_player_set_tempo)(fluid_player_t*, int, double);

// ==================== Global Function Pointers ====================

static void* g_libHandle = nullptr;
static bool g_initialized = false;

static fn_new_fluid_settings p_new_fluid_settings = nullptr;
static fn_delete_fluid_settings p_delete_fluid_settings = nullptr;
static fn_fluid_settings_setstr p_fluid_settings_setstr = nullptr;
static fn_fluid_settings_setnum p_fluid_settings_setnum = nullptr;
static fn_fluid_settings_setint p_fluid_settings_setint = nullptr;

static fn_new_fluid_synth p_new_fluid_synth = nullptr;
static fn_delete_fluid_synth p_delete_fluid_synth = nullptr;
static fn_fluid_synth_sfload p_fluid_synth_sfload = nullptr;
static fn_fluid_synth_sfunload p_fluid_synth_sfunload = nullptr;
static fn_fluid_synth_noteon p_fluid_synth_noteon = nullptr;
static fn_fluid_synth_noteoff p_fluid_synth_noteoff = nullptr;
static fn_fluid_synth_program_change p_fluid_synth_program_change = nullptr;
static fn_fluid_synth_bank_select p_fluid_synth_bank_select = nullptr;
static fn_fluid_synth_cc p_fluid_synth_cc = nullptr;
static fn_fluid_synth_pitch_bend p_fluid_synth_pitch_bend = nullptr;
static fn_fluid_synth_all_notes_off p_fluid_synth_all_notes_off = nullptr;
static fn_fluid_synth_all_sounds_off p_fluid_synth_all_sounds_off = nullptr;
static fn_fluid_synth_system_reset p_fluid_synth_system_reset = nullptr;
static fn_fluid_synth_set_gain p_fluid_synth_set_gain = nullptr;
static fn_fluid_synth_get_gain p_fluid_synth_get_gain = nullptr;
static fn_fluid_synth_get_polyphony p_fluid_synth_get_polyphony = nullptr;
static fn_fluid_synth_get_active_voice_count p_fluid_synth_get_active_voice_count = nullptr;
static fn_fluid_synth_set_reverb_on p_fluid_synth_set_reverb_on = nullptr;
static fn_fluid_synth_reverb_on p_fluid_synth_reverb_on = nullptr;
static fn_fluid_synth_set_reverb p_fluid_synth_set_reverb = nullptr;
static fn_fluid_synth_set_chorus_on p_fluid_synth_set_chorus_on = nullptr;
static fn_fluid_synth_chorus_on p_fluid_synth_chorus_on = nullptr;
static fn_fluid_synth_write_s16 p_fluid_synth_write_s16 = nullptr;
static fn_fluid_synth_write_float p_fluid_synth_write_float = nullptr;

static fn_new_fluid_audio_driver p_new_fluid_audio_driver = nullptr;
static fn_delete_fluid_audio_driver p_delete_fluid_audio_driver = nullptr;

static fn_new_fluid_player p_new_fluid_player = nullptr;
static fn_delete_fluid_player p_delete_fluid_player = nullptr;
static fn_fluid_player_add p_fluid_player_add = nullptr;
static fn_fluid_player_play p_fluid_player_play = nullptr;
static fn_fluid_player_stop p_fluid_player_stop = nullptr;
static fn_fluid_player_get_status p_fluid_player_get_status = nullptr;
static fn_fluid_player_seek p_fluid_player_seek = nullptr;
static fn_fluid_player_get_current_tick p_fluid_player_get_current_tick = nullptr;
static fn_fluid_player_get_total_ticks p_fluid_player_get_total_ticks = nullptr;
static fn_fluid_player_set_loop p_fluid_player_set_loop = nullptr;
static fn_fluid_player_set_tempo p_fluid_player_set_tempo = nullptr;

// ==================== Library Loading ====================

#define LOAD_SYMBOL(name) \
    p_##name = (fn_##name)dlsym(g_libHandle, #name); \
    if (!p_##name) { \
        LOGW("Failed to load symbol: %s", #name); \
    }

static bool loadFluidSynthLibrary() {
    if (g_initialized) return true;

    // Primary: find the library already loaded in the process by the Kotlin-side
    // System.loadLibrary("fluidsynth") call. RTLD_NOLOAD returns a handle without
    // re-loading — it succeeds only if the library is already in the process namespace.
    g_libHandle = dlopen("libfluidsynth.so", RTLD_NOLOAD | RTLD_NOW);
    if (g_libHandle) {
        LOGI("Found libfluidsynth.so already loaded in process (RTLD_NOLOAD)");
    }

    // Fallback: try to load from disk (works when extractNativeLibs=true or
    // when the library is on LD_LIBRARY_PATH)
    if (!g_libHandle) {
        const char* libNames[] = {
            "libfluidsynth.so",
            "libfluidsynth-assetloader.so",
            nullptr
        };
        for (int i = 0; libNames[i] != nullptr; i++) {
            g_libHandle = dlopen(libNames[i], RTLD_NOW);
            if (g_libHandle) {
                LOGI("Loaded FluidSynth library: %s", libNames[i]);
                break;
            }
            LOGD("dlopen(%s) failed: %s", libNames[i], dlerror());
        }
    }

    if (!g_libHandle) {
        LOGE("Failed to load FluidSynth library: %s", dlerror());
        return false;
    }

    // Load all function pointers
    LOAD_SYMBOL(new_fluid_settings);
    LOAD_SYMBOL(delete_fluid_settings);
    LOAD_SYMBOL(fluid_settings_setstr);
    LOAD_SYMBOL(fluid_settings_setnum);
    LOAD_SYMBOL(fluid_settings_setint);

    LOAD_SYMBOL(new_fluid_synth);
    LOAD_SYMBOL(delete_fluid_synth);
    LOAD_SYMBOL(fluid_synth_sfload);
    LOAD_SYMBOL(fluid_synth_sfunload);
    LOAD_SYMBOL(fluid_synth_noteon);
    LOAD_SYMBOL(fluid_synth_noteoff);
    LOAD_SYMBOL(fluid_synth_program_change);
    LOAD_SYMBOL(fluid_synth_bank_select);
    LOAD_SYMBOL(fluid_synth_cc);
    LOAD_SYMBOL(fluid_synth_pitch_bend);
    LOAD_SYMBOL(fluid_synth_all_notes_off);
    LOAD_SYMBOL(fluid_synth_all_sounds_off);
    LOAD_SYMBOL(fluid_synth_system_reset);
    LOAD_SYMBOL(fluid_synth_set_gain);
    LOAD_SYMBOL(fluid_synth_get_gain);
    LOAD_SYMBOL(fluid_synth_get_polyphony);
    LOAD_SYMBOL(fluid_synth_get_active_voice_count);
    LOAD_SYMBOL(fluid_synth_set_reverb_on);
    LOAD_SYMBOL(fluid_synth_reverb_on);
    LOAD_SYMBOL(fluid_synth_set_reverb);
    LOAD_SYMBOL(fluid_synth_set_chorus_on);
    LOAD_SYMBOL(fluid_synth_chorus_on);
    LOAD_SYMBOL(fluid_synth_write_s16);
    LOAD_SYMBOL(fluid_synth_write_float);

    LOAD_SYMBOL(new_fluid_audio_driver);
    LOAD_SYMBOL(delete_fluid_audio_driver);

    LOAD_SYMBOL(new_fluid_player);
    LOAD_SYMBOL(delete_fluid_player);
    LOAD_SYMBOL(fluid_player_add);
    LOAD_SYMBOL(fluid_player_play);
    LOAD_SYMBOL(fluid_player_stop);
    LOAD_SYMBOL(fluid_player_get_status);
    LOAD_SYMBOL(fluid_player_seek);
    LOAD_SYMBOL(fluid_player_get_current_tick);
    LOAD_SYMBOL(fluid_player_get_total_ticks);
    LOAD_SYMBOL(fluid_player_set_loop);
    LOAD_SYMBOL(fluid_player_set_tempo);

    // Check critical functions
    if (!p_new_fluid_settings || !p_new_fluid_synth || !p_fluid_synth_noteon) {
        LOGE("Critical FluidSynth functions not found");
        dlclose(g_libHandle);
        g_libHandle = nullptr;
        return false;
    }

    g_initialized = true;
    LOGI("FluidSynth library loaded successfully");
    return true;
}

extern "C" {

// ==================== Library Loading JNI ====================

JNIEXPORT jboolean JNICALL
Java_com_Atom2Universe_app_midi_fluidsynth_FluidSynthNative_nativeLoadLibrary(JNIEnv*, jclass) {
    return loadFluidSynthLibrary() ? JNI_TRUE : JNI_FALSE;
}

// ==================== Settings ====================

JNIEXPORT jlong JNICALL
Java_com_Atom2Universe_app_midi_fluidsynth_FluidSynthNative_newSettings(JNIEnv*, jclass) {
    if (!g_initialized || !p_new_fluid_settings) return 0;

    fluid_settings_t *settings = p_new_fluid_settings();
    if (settings == nullptr) {
        LOGE("Failed to create FluidSynth settings");
        return 0;
    }

    // Configure for Android/low-latency
    if (p_fluid_settings_setstr) p_fluid_settings_setstr(settings, "audio.driver", "oboe");
    if (p_fluid_settings_setint) {
        p_fluid_settings_setint(settings, "audio.periods", 2);
        p_fluid_settings_setint(settings, "audio.period-size", 256);
        p_fluid_settings_setint(settings, "synth.polyphony", 128);
        p_fluid_settings_setint(settings, "synth.cpu-cores", 2);
    }
    if (p_fluid_settings_setnum) p_fluid_settings_setnum(settings, "synth.sample-rate", 48000.0);

    LOGD("Created FluidSynth settings");
    return reinterpret_cast<jlong>(settings);
}

JNIEXPORT void JNICALL
Java_com_Atom2Universe_app_midi_fluidsynth_FluidSynthNative_deleteSettings(JNIEnv*, jclass, jlong settingsHandle) {
    if (settingsHandle == 0 || !p_delete_fluid_settings) return;
    fluid_settings_t *settings = reinterpret_cast<fluid_settings_t*>(settingsHandle);
    p_delete_fluid_settings(settings);
    LOGD("Deleted FluidSynth settings");
}

JNIEXPORT jboolean JNICALL
Java_com_Atom2Universe_app_midi_fluidsynth_FluidSynthNative_setSettingStr(JNIEnv *env, jclass,
        jlong settingsHandle, jstring name, jstring value) {
    if (settingsHandle == 0 || !p_fluid_settings_setstr) return JNI_FALSE;
    fluid_settings_t *settings = reinterpret_cast<fluid_settings_t*>(settingsHandle);

    const char *nameStr = env->GetStringUTFChars(name, nullptr);
    const char *valueStr = env->GetStringUTFChars(value, nullptr);

    int result = p_fluid_settings_setstr(settings, nameStr, valueStr);

    env->ReleaseStringUTFChars(name, nameStr);
    env->ReleaseStringUTFChars(value, valueStr);

    return result == FLUID_OK ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_Atom2Universe_app_midi_fluidsynth_FluidSynthNative_setSettingInt(JNIEnv *env, jclass,
        jlong settingsHandle, jstring name, jint value) {
    if (settingsHandle == 0 || !p_fluid_settings_setint) return JNI_FALSE;
    fluid_settings_t *settings = reinterpret_cast<fluid_settings_t*>(settingsHandle);

    const char *nameStr = env->GetStringUTFChars(name, nullptr);
    int result = p_fluid_settings_setint(settings, nameStr, value);
    env->ReleaseStringUTFChars(name, nameStr);

    return result == FLUID_OK ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_Atom2Universe_app_midi_fluidsynth_FluidSynthNative_setSettingNum(JNIEnv *env, jclass,
        jlong settingsHandle, jstring name, jdouble value) {
    if (settingsHandle == 0 || !p_fluid_settings_setnum) return JNI_FALSE;
    fluid_settings_t *settings = reinterpret_cast<fluid_settings_t*>(settingsHandle);

    const char *nameStr = env->GetStringUTFChars(name, nullptr);
    int result = p_fluid_settings_setnum(settings, nameStr, value);
    env->ReleaseStringUTFChars(name, nameStr);

    return result == FLUID_OK ? JNI_TRUE : JNI_FALSE;
}

// ==================== Synth ====================

JNIEXPORT jlong JNICALL
Java_com_Atom2Universe_app_midi_fluidsynth_FluidSynthNative_newSynth(JNIEnv*, jclass, jlong settingsHandle) {
    if (settingsHandle == 0 || !p_new_fluid_synth) {
        LOGE("Cannot create synth: settings handle is null or library not loaded");
        return 0;
    }

    fluid_settings_t *settings = reinterpret_cast<fluid_settings_t*>(settingsHandle);
    fluid_synth_t *synth = p_new_fluid_synth(settings);

    if (synth == nullptr) {
        LOGE("Failed to create FluidSynth synth");
        return 0;
    }

    LOGI("Created FluidSynth synth");
    return reinterpret_cast<jlong>(synth);
}

JNIEXPORT void JNICALL
Java_com_Atom2Universe_app_midi_fluidsynth_FluidSynthNative_deleteSynth(JNIEnv*, jclass, jlong synthHandle) {
    if (synthHandle == 0 || !p_delete_fluid_synth) return;
    fluid_synth_t *synth = reinterpret_cast<fluid_synth_t*>(synthHandle);
    p_delete_fluid_synth(synth);
    LOGI("Deleted FluidSynth synth");
}

// ==================== SoundFont ====================

JNIEXPORT jint JNICALL
Java_com_Atom2Universe_app_midi_fluidsynth_FluidSynthNative_sfLoad(JNIEnv *env, jclass,
        jlong synthHandle, jstring path, jboolean resetPresets) {
    if (synthHandle == 0 || !p_fluid_synth_sfload) return -1;
    fluid_synth_t *synth = reinterpret_cast<fluid_synth_t*>(synthHandle);

    const char *pathStr = env->GetStringUTFChars(path, nullptr);
    LOGI("Loading SoundFont: %s", pathStr);

    int sfId = p_fluid_synth_sfload(synth, pathStr, resetPresets ? 1 : 0);

    env->ReleaseStringUTFChars(path, pathStr);

    if (sfId == FLUID_FAILED) {
        LOGE("Failed to load SoundFont");
        return -1;
    }

    LOGI("Loaded SoundFont with ID: %d", sfId);
    return sfId;
}

JNIEXPORT jboolean JNICALL
Java_com_Atom2Universe_app_midi_fluidsynth_FluidSynthNative_sfUnload(JNIEnv*, jclass,
        jlong synthHandle, jint sfId, jboolean resetPresets) {
    if (synthHandle == 0 || !p_fluid_synth_sfunload) return JNI_FALSE;
    fluid_synth_t *synth = reinterpret_cast<fluid_synth_t*>(synthHandle);

    int result = p_fluid_synth_sfunload(synth, sfId, resetPresets ? 1 : 0);
    return result == FLUID_OK ? JNI_TRUE : JNI_FALSE;
}

// ==================== MIDI Events ====================

JNIEXPORT jboolean JNICALL
Java_com_Atom2Universe_app_midi_fluidsynth_FluidSynthNative_noteOn(JNIEnv*, jclass,
        jlong synthHandle, jint channel, jint note, jint velocity) {
    if (synthHandle == 0 || !p_fluid_synth_noteon) return JNI_FALSE;
    fluid_synth_t *synth = reinterpret_cast<fluid_synth_t*>(synthHandle);

    int result = p_fluid_synth_noteon(synth, channel, note, velocity);
    return result == FLUID_OK ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_Atom2Universe_app_midi_fluidsynth_FluidSynthNative_noteOff(JNIEnv*, jclass,
        jlong synthHandle, jint channel, jint note) {
    if (synthHandle == 0 || !p_fluid_synth_noteoff) return JNI_FALSE;
    fluid_synth_t *synth = reinterpret_cast<fluid_synth_t*>(synthHandle);

    int result = p_fluid_synth_noteoff(synth, channel, note);
    return result == FLUID_OK ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_Atom2Universe_app_midi_fluidsynth_FluidSynthNative_programChange(JNIEnv*, jclass,
        jlong synthHandle, jint channel, jint program) {
    if (synthHandle == 0 || !p_fluid_synth_program_change) return JNI_FALSE;
    fluid_synth_t *synth = reinterpret_cast<fluid_synth_t*>(synthHandle);

    int result = p_fluid_synth_program_change(synth, channel, program);
    return result == FLUID_OK ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_Atom2Universe_app_midi_fluidsynth_FluidSynthNative_bankSelect(JNIEnv*, jclass,
        jlong synthHandle, jint channel, jint bank) {
    if (synthHandle == 0 || !p_fluid_synth_bank_select) return JNI_FALSE;
    fluid_synth_t *synth = reinterpret_cast<fluid_synth_t*>(synthHandle);

    int result = p_fluid_synth_bank_select(synth, channel, bank);
    return result == FLUID_OK ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_Atom2Universe_app_midi_fluidsynth_FluidSynthNative_cc(JNIEnv*, jclass,
        jlong synthHandle, jint channel, jint controller, jint value) {
    if (synthHandle == 0 || !p_fluid_synth_cc) return JNI_FALSE;
    fluid_synth_t *synth = reinterpret_cast<fluid_synth_t*>(synthHandle);

    int result = p_fluid_synth_cc(synth, channel, controller, value);
    return result == FLUID_OK ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_Atom2Universe_app_midi_fluidsynth_FluidSynthNative_pitchBend(JNIEnv*, jclass,
        jlong synthHandle, jint channel, jint value) {
    if (synthHandle == 0 || !p_fluid_synth_pitch_bend) return JNI_FALSE;
    fluid_synth_t *synth = reinterpret_cast<fluid_synth_t*>(synthHandle);

    int result = p_fluid_synth_pitch_bend(synth, channel, value);
    return result == FLUID_OK ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_Atom2Universe_app_midi_fluidsynth_FluidSynthNative_allNotesOff(JNIEnv*, jclass,
        jlong synthHandle, jint channel) {
    if (synthHandle == 0 || !p_fluid_synth_all_notes_off) return JNI_FALSE;
    fluid_synth_t *synth = reinterpret_cast<fluid_synth_t*>(synthHandle);

    int result = p_fluid_synth_all_notes_off(synth, channel);
    return result == FLUID_OK ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_Atom2Universe_app_midi_fluidsynth_FluidSynthNative_allSoundOff(JNIEnv*, jclass,
        jlong synthHandle, jint channel) {
    if (synthHandle == 0 || !p_fluid_synth_all_sounds_off) return JNI_FALSE;
    fluid_synth_t *synth = reinterpret_cast<fluid_synth_t*>(synthHandle);

    int result = p_fluid_synth_all_sounds_off(synth, channel);
    return result == FLUID_OK ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_Atom2Universe_app_midi_fluidsynth_FluidSynthNative_systemReset(JNIEnv*, jclass,
        jlong synthHandle) {
    if (synthHandle == 0 || !p_fluid_synth_system_reset) return;
    fluid_synth_t *synth = reinterpret_cast<fluid_synth_t*>(synthHandle);
    p_fluid_synth_system_reset(synth);
}

// ==================== Synth Parameters ====================

JNIEXPORT void JNICALL
Java_com_Atom2Universe_app_midi_fluidsynth_FluidSynthNative_setGain(JNIEnv*, jclass,
        jlong synthHandle, jfloat gain) {
    if (synthHandle == 0 || !p_fluid_synth_set_gain) return;
    fluid_synth_t *synth = reinterpret_cast<fluid_synth_t*>(synthHandle);
    p_fluid_synth_set_gain(synth, gain);
}

JNIEXPORT jfloat JNICALL
Java_com_Atom2Universe_app_midi_fluidsynth_FluidSynthNative_getGain(JNIEnv*, jclass,
        jlong synthHandle) {
    if (synthHandle == 0 || !p_fluid_synth_get_gain) return 0.0f;
    fluid_synth_t *synth = reinterpret_cast<fluid_synth_t*>(synthHandle);
    return p_fluid_synth_get_gain(synth);
}

JNIEXPORT jint JNICALL
Java_com_Atom2Universe_app_midi_fluidsynth_FluidSynthNative_getPolyphony(JNIEnv*, jclass,
        jlong synthHandle) {
    if (synthHandle == 0 || !p_fluid_synth_get_polyphony) return 0;
    fluid_synth_t *synth = reinterpret_cast<fluid_synth_t*>(synthHandle);
    return p_fluid_synth_get_polyphony(synth);
}

JNIEXPORT jint JNICALL
Java_com_Atom2Universe_app_midi_fluidsynth_FluidSynthNative_getActiveVoiceCount(JNIEnv*, jclass,
        jlong synthHandle) {
    if (synthHandle == 0 || !p_fluid_synth_get_active_voice_count) return 0;
    fluid_synth_t *synth = reinterpret_cast<fluid_synth_t*>(synthHandle);
    return p_fluid_synth_get_active_voice_count(synth);
}

// ==================== Reverb ====================

JNIEXPORT void JNICALL
Java_com_Atom2Universe_app_midi_fluidsynth_FluidSynthNative_setReverbOn(JNIEnv*, jclass,
        jlong synthHandle, jboolean on) {
    if (synthHandle == 0) return;
    fluid_synth_t *synth = reinterpret_cast<fluid_synth_t*>(synthHandle);
    if (p_fluid_synth_reverb_on) {
        p_fluid_synth_reverb_on(synth, -1, on ? 1 : 0);
    } else if (p_fluid_synth_set_reverb_on) {
        p_fluid_synth_set_reverb_on(synth, on ? 1 : 0);
    }
}

JNIEXPORT void JNICALL
Java_com_Atom2Universe_app_midi_fluidsynth_FluidSynthNative_setReverb(JNIEnv*, jclass,
        jlong synthHandle, jdouble roomSize, jdouble damping, jdouble width, jdouble level) {
    if (synthHandle == 0 || !p_fluid_synth_set_reverb) return;
    fluid_synth_t *synth = reinterpret_cast<fluid_synth_t*>(synthHandle);
    p_fluid_synth_set_reverb(synth, roomSize, damping, width, level);
}

// ==================== Chorus ====================

JNIEXPORT void JNICALL
Java_com_Atom2Universe_app_midi_fluidsynth_FluidSynthNative_setChorusOn(JNIEnv*, jclass,
        jlong synthHandle, jboolean on) {
    if (synthHandle == 0) return;
    fluid_synth_t *synth = reinterpret_cast<fluid_synth_t*>(synthHandle);
    if (p_fluid_synth_chorus_on) {
        p_fluid_synth_chorus_on(synth, -1, on ? 1 : 0);
    } else if (p_fluid_synth_set_chorus_on) {
        p_fluid_synth_set_chorus_on(synth, on ? 1 : 0);
    }
}

// ==================== Audio Rendering ====================

JNIEXPORT jint JNICALL
Java_com_Atom2Universe_app_midi_fluidsynth_FluidSynthNative_writeStereoShort(JNIEnv *env, jclass,
        jlong synthHandle, jshortArray buffer, jint offset, jint frames) {
    if (synthHandle == 0 || !p_fluid_synth_write_s16) return -1;
    fluid_synth_t *synth = reinterpret_cast<fluid_synth_t*>(synthHandle);

    jshort *bufferPtr = env->GetShortArrayElements(buffer, nullptr);
    if (bufferPtr == nullptr) return -1;

    // FluidSynth writes interleaved stereo samples
    int result = p_fluid_synth_write_s16(synth, frames,
            bufferPtr + offset, 0, 2,  // left channel
            bufferPtr + offset, 1, 2); // right channel

    env->ReleaseShortArrayElements(buffer, bufferPtr, 0);

    return result == FLUID_OK ? frames : -1;
}

JNIEXPORT jint JNICALL
Java_com_Atom2Universe_app_midi_fluidsynth_FluidSynthNative_writeStereoFloat(JNIEnv *env, jclass,
        jlong synthHandle, jfloatArray leftBuffer, jfloatArray rightBuffer, jint frames) {
    if (synthHandle == 0 || !p_fluid_synth_write_float) return -1;
    fluid_synth_t *synth = reinterpret_cast<fluid_synth_t*>(synthHandle);

    jfloat *leftPtr = env->GetFloatArrayElements(leftBuffer, nullptr);
    jfloat *rightPtr = env->GetFloatArrayElements(rightBuffer, nullptr);

    if (leftPtr == nullptr || rightPtr == nullptr) {
        if (leftPtr) env->ReleaseFloatArrayElements(leftBuffer, leftPtr, 0);
        if (rightPtr) env->ReleaseFloatArrayElements(rightBuffer, rightPtr, 0);
        return -1;
    }

    int result = p_fluid_synth_write_float(synth, frames,
            leftPtr, 0, 1,
            rightPtr, 0, 1);

    env->ReleaseFloatArrayElements(leftBuffer, leftPtr, 0);
    env->ReleaseFloatArrayElements(rightBuffer, rightPtr, 0);

    return result == FLUID_OK ? frames : -1;
}

// ==================== Audio Driver ====================

JNIEXPORT jlong JNICALL
Java_com_Atom2Universe_app_midi_fluidsynth_FluidSynthNative_newAudioDriver(JNIEnv*, jclass,
        jlong settingsHandle, jlong synthHandle) {
    if (settingsHandle == 0 || synthHandle == 0 || !p_new_fluid_audio_driver) return 0;

    fluid_settings_t *settings = reinterpret_cast<fluid_settings_t*>(settingsHandle);
    fluid_synth_t *synth = reinterpret_cast<fluid_synth_t*>(synthHandle);

    fluid_audio_driver_t *driver = p_new_fluid_audio_driver(settings, synth);
    if (driver == nullptr) {
        LOGE("Failed to create FluidSynth audio driver");
        return 0;
    }

    LOGI("Created FluidSynth audio driver");
    return reinterpret_cast<jlong>(driver);
}

JNIEXPORT void JNICALL
Java_com_Atom2Universe_app_midi_fluidsynth_FluidSynthNative_deleteAudioDriver(JNIEnv*, jclass,
        jlong driverHandle) {
    if (driverHandle == 0 || !p_delete_fluid_audio_driver) return;
    fluid_audio_driver_t *driver = reinterpret_cast<fluid_audio_driver_t*>(driverHandle);
    p_delete_fluid_audio_driver(driver);
    LOGI("Deleted FluidSynth audio driver");
}

// ==================== MIDI Player ====================

JNIEXPORT jlong JNICALL
Java_com_Atom2Universe_app_midi_fluidsynth_FluidSynthNative_newPlayer(JNIEnv*, jclass,
        jlong synthHandle) {
    if (synthHandle == 0 || !p_new_fluid_player) return 0;
    fluid_synth_t *synth = reinterpret_cast<fluid_synth_t*>(synthHandle);

    fluid_player_t *player = p_new_fluid_player(synth);
    if (player == nullptr) {
        LOGE("Failed to create FluidSynth player");
        return 0;
    }

    LOGD("Created FluidSynth player");
    return reinterpret_cast<jlong>(player);
}

JNIEXPORT void JNICALL
Java_com_Atom2Universe_app_midi_fluidsynth_FluidSynthNative_deletePlayer(JNIEnv*, jclass,
        jlong playerHandle) {
    if (playerHandle == 0 || !p_delete_fluid_player) return;
    fluid_player_t *player = reinterpret_cast<fluid_player_t*>(playerHandle);
    p_delete_fluid_player(player);
    LOGD("Deleted FluidSynth player");
}

JNIEXPORT jboolean JNICALL
Java_com_Atom2Universe_app_midi_fluidsynth_FluidSynthNative_playerAdd(JNIEnv *env, jclass,
        jlong playerHandle, jstring midiPath) {
    if (playerHandle == 0 || !p_fluid_player_add) return JNI_FALSE;
    fluid_player_t *player = reinterpret_cast<fluid_player_t*>(playerHandle);

    const char *pathStr = env->GetStringUTFChars(midiPath, nullptr);
    LOGD("Adding MIDI file: %s", pathStr);

    int result = p_fluid_player_add(player, pathStr);

    env->ReleaseStringUTFChars(midiPath, pathStr);

    return result == FLUID_OK ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_Atom2Universe_app_midi_fluidsynth_FluidSynthNative_playerPlay(JNIEnv*, jclass,
        jlong playerHandle) {
    if (playerHandle == 0 || !p_fluid_player_play) return JNI_FALSE;
    fluid_player_t *player = reinterpret_cast<fluid_player_t*>(playerHandle);

    int result = p_fluid_player_play(player);
    return result == FLUID_OK ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_Atom2Universe_app_midi_fluidsynth_FluidSynthNative_playerStop(JNIEnv*, jclass,
        jlong playerHandle) {
    if (playerHandle == 0 || !p_fluid_player_stop) return;
    fluid_player_t *player = reinterpret_cast<fluid_player_t*>(playerHandle);
    p_fluid_player_stop(player);
}

JNIEXPORT jint JNICALL
Java_com_Atom2Universe_app_midi_fluidsynth_FluidSynthNative_playerGetStatus(JNIEnv*, jclass,
        jlong playerHandle) {
    if (playerHandle == 0 || !p_fluid_player_get_status) return -1;
    fluid_player_t *player = reinterpret_cast<fluid_player_t*>(playerHandle);
    return p_fluid_player_get_status(player);
}

JNIEXPORT jint JNICALL
Java_com_Atom2Universe_app_midi_fluidsynth_FluidSynthNative_playerSeek(JNIEnv*, jclass,
        jlong playerHandle, jint ticks) {
    if (playerHandle == 0 || !p_fluid_player_seek) return -1;
    fluid_player_t *player = reinterpret_cast<fluid_player_t*>(playerHandle);
    return p_fluid_player_seek(player, ticks);
}

JNIEXPORT jint JNICALL
Java_com_Atom2Universe_app_midi_fluidsynth_FluidSynthNative_playerGetCurrentTick(JNIEnv*, jclass,
        jlong playerHandle) {
    if (playerHandle == 0 || !p_fluid_player_get_current_tick) return -1;
    fluid_player_t *player = reinterpret_cast<fluid_player_t*>(playerHandle);
    return p_fluid_player_get_current_tick(player);
}

JNIEXPORT jint JNICALL
Java_com_Atom2Universe_app_midi_fluidsynth_FluidSynthNative_playerGetTotalTicks(JNIEnv*, jclass,
        jlong playerHandle) {
    if (playerHandle == 0 || !p_fluid_player_get_total_ticks) return -1;
    fluid_player_t *player = reinterpret_cast<fluid_player_t*>(playerHandle);
    return p_fluid_player_get_total_ticks(player);
}

JNIEXPORT void JNICALL
Java_com_Atom2Universe_app_midi_fluidsynth_FluidSynthNative_playerSetLoop(JNIEnv*, jclass,
        jlong playerHandle, jint loops) {
    if (playerHandle == 0 || !p_fluid_player_set_loop) return;
    fluid_player_t *player = reinterpret_cast<fluid_player_t*>(playerHandle);
    p_fluid_player_set_loop(player, loops);
}

JNIEXPORT void JNICALL
Java_com_Atom2Universe_app_midi_fluidsynth_FluidSynthNative_playerSetTempo(JNIEnv*, jclass,
        jlong playerHandle, jint tempoType, jdouble tempo) {
    if (playerHandle == 0 || !p_fluid_player_set_tempo) return;
    fluid_player_t *player = reinterpret_cast<fluid_player_t*>(playerHandle);
    p_fluid_player_set_tempo(player, tempoType, tempo);
}

// ==================== Utility ====================

JNIEXPORT jstring JNICALL
Java_com_Atom2Universe_app_midi_fluidsynth_FluidSynthNative_getVersion(JNIEnv *env, jclass) {
    char version[64];
    snprintf(version, sizeof(version), "%d.%d.%d",
             FLUIDSYNTH_VERSION_MAJOR,
             FLUIDSYNTH_VERSION_MINOR,
             FLUIDSYNTH_VERSION_MICRO);
    return env->NewStringUTF(version);
}

} // extern "C"
