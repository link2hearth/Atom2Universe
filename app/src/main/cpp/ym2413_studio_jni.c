#include <jni.h>
#include <stdint.h>
#include "emu2413/emu2413.h"

/* emu2413 creates the original YM2413 ROM and initialises its native clock/72 rate converter. */
JNIEXPORT jlong JNICALL Java_com_Atom2Universe_app_studio_dsp_Ym2413Native_create(
    JNIEnv *env, jobject self, jint rate) {
    return (jlong)(intptr_t)OPLL_new(3579545, (uint32_t)rate);
}

JNIEXPORT void JNICALL Java_com_Atom2Universe_app_studio_dsp_Ym2413Native_destroy(
    JNIEnv *env, jobject self, jlong id) {
    OPLL_delete((OPLL *)(intptr_t)id);
}

JNIEXPORT void JNICALL Java_com_Atom2Universe_app_studio_dsp_Ym2413Native_write(
    JNIEnv *env, jobject self, jlong id, jint reg, jint value) {
    OPLL_writeReg((OPLL *)(intptr_t)id, (uint32_t)reg, (uint8_t)value);
}

/* Return a bit per channel whose carrier still has a tail. No chip reset between blocks. */
JNIEXPORT jint JNICALL Java_com_Atom2Universe_app_studio_dsp_Ym2413Native_render(
    JNIEnv *env, jobject self, jlong id, jfloatArray output, jint offset, jint count) {
    OPLL *chip = (OPLL *)(intptr_t)id;
    jfloat block[256];
    while (count > 0) {
        const jint size = count < 256 ? count : 256;
        for (jint i = 0; i < size; ++i) {
            /* Fixed gain shared by all factory voices to fit the Studio mixer. */
            block[i] = OPLL_calc(chip) * (1.0f / 8192.0f);
        }
        (*env)->SetFloatArrayRegion(env, output, offset, size, block);
        if ((*env)->ExceptionCheck(env)) return 0;
        offset += size;
        count -= size;
    }
    jint audible = 0;
    for (int i = 0; i < 9; ++i) {
        if (chip->slot[2 * i + 1].eg_out < 127) audible |= 1 << i;
    }
    return audible;
}
