////////////////////////////////////////////////////////////////////////////////
//
//  MidiDriver - An Android Midi Driver.
//
//  Copyright (C) 2013	Bill Farmer
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.
//
//  Bill Farmer	 william j farmer [at] yahoo [dot] co [dot] uk.
//


#include <jni.h>
#include <assert.h>
#include <pthread.h>
#include <cmath>

#include <android/log.h>

// for oboe native audio
#include <oboe/Oboe.h>

// for EAS midi
#define DLS_SYNTHESIZER
#include "eas.h"
#include "eas_reverb.h"

// for EAS_HWMemCpy
#include "eas_host.h"

#include "org_billthefarmer_mididriver_MidiDriver.h"
#include "midi.h"

#define LOG_TAG "MidiDriver"

#define LOG_D(tag, ...) __android_log_print(ANDROID_LOG_DEBUG, tag, __VA_ARGS__)
#define LOG_W(tag, ...) __android_log_print(ANDROID_LOG_WARN, tag, __VA_ARGS__)
#define LOG_E(tag, ...) __android_log_print(ANDROID_LOG_ERROR, tag, __VA_ARGS__)
#define LOG_I(tag, ...) __android_log_print(ANDROID_LOG_INFO, tag, __VA_ARGS__)

// determines how many EAS buffers to fill a host buffer
#define NUM_BUFFERS 4

// typedef
typedef struct
{
    int len;
    const EAS_U8 *data;
} EAS_DLS_HANDLE;

// mutex
static pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;

// oboe stream
std::shared_ptr<oboe::AudioStream> oboeStream;

// preferred output device ID (-1 = default/auto)
static int32_t preferredDeviceId = oboe::kUnspecified;

// EAS lib config (forward declaration needed by EQ)
const S_EAS_LIB_CONFIG *pLibConfig;

// ==================== EQ 10-band biquad (RBJ Audio EQ Cookbook) ====================

#define EQ_BANDS 10
#define EQ_Q     1.41f

// Centre frequencies (Hz): 32, 64, 125, 250, 500, 1k, 2k, 4k, 8k, 16k
static const float EQ_FREQ_HZ[EQ_BANDS] = {32, 64, 125, 250, 500, 1000, 2000, 4000, 8000, 16000};
// Filter type per band: 0=LOW_SHELF, 1=PEAK, 2=HIGH_SHELF
static const int EQ_TYPE[EQ_BANDS] = {0, 1, 1, 1, 1, 1, 1, 1, 1, 2};

typedef struct {
    float b0, b1, b2, a1, a2;
    bool bypassed;
} BiquadCoeffs;

typedef struct {
    float x1, x2, y1, y2;
} BiquadState;

static bool          eqEnabled           = false;
static BiquadCoeffs  eqCoeffs[EQ_BANDS];
static BiquadState   eqStateL[EQ_BANDS];
static BiquadState   eqStateR[EQ_BANDS];

static void initEqCoeffs()
{
    for (int i = 0; i < EQ_BANDS; i++)
    {
        eqCoeffs[i].b0       = 1.0f;
        eqCoeffs[i].b1       = 0.0f;
        eqCoeffs[i].b2       = 0.0f;
        eqCoeffs[i].a1       = 0.0f;
        eqCoeffs[i].a2       = 0.0f;
        eqCoeffs[i].bypassed = true;
        eqStateL[i] = {0.0f, 0.0f, 0.0f, 0.0f};
        eqStateR[i] = {0.0f, 0.0f, 0.0f, 0.0f};
    }
}

static void computeEqCoeffs(int bandIdx, float gainDb)
{
    if (bandIdx < 0 || bandIdx >= EQ_BANDS) return;

    BiquadCoeffs &c = eqCoeffs[bandIdx];

    if (gainDb == 0.0f)
    {
        c.bypassed = true;
        return;
    }
    c.bypassed = false;

    float sampleRate = (pLibConfig != NULL) ? (float)pLibConfig->sampleRate : 22050.0f;
    float freqHz     = EQ_FREQ_HZ[bandIdx];
    int   type       = EQ_TYPE[bandIdx];

    float A     = powf(10.0f, gainDb / 40.0f);
    float w0    = 2.0f * (float)M_PI * freqHz / sampleRate;
    float cosW0 = cosf(w0);
    float sinW0 = sinf(w0);
    float alpha = sinW0 / (2.0f * EQ_Q);

    float rawB0, rawB1, rawB2, rawA0, rawA1, rawA2;

    if (type == 1) // PEAK
    {
        rawB0 = 1.0f + alpha * A;
        rawB1 = -2.0f * cosW0;
        rawB2 = 1.0f - alpha * A;
        rawA0 = 1.0f + alpha / A;
        rawA1 = -2.0f * cosW0;
        rawA2 = 1.0f - alpha / A;
    }
    else if (type == 0) // LOW_SHELF
    {
        float sqrtA  = sqrtf(A);
        float alphaS = sinW0 / 2.0f * sqrtf((A + 1.0f / A) * (1.0f / EQ_Q - 1.0f) + 2.0f);
        rawB0 =  A * ((A + 1.0f) - (A - 1.0f) * cosW0 + 2.0f * sqrtA * alphaS);
        rawB1 = 2.0f * A * ((A - 1.0f) - (A + 1.0f) * cosW0);
        rawB2 =  A * ((A + 1.0f) - (A - 1.0f) * cosW0 - 2.0f * sqrtA * alphaS);
        rawA0 =       (A + 1.0f) + (A - 1.0f) * cosW0 + 2.0f * sqrtA * alphaS;
        rawA1 = -2.0f * ((A - 1.0f) + (A + 1.0f) * cosW0);
        rawA2 =       (A + 1.0f) + (A - 1.0f) * cosW0 - 2.0f * sqrtA * alphaS;
    }
    else // HIGH_SHELF
    {
        float sqrtA  = sqrtf(A);
        float alphaS = sinW0 / 2.0f * sqrtf((A + 1.0f / A) * (1.0f / EQ_Q - 1.0f) + 2.0f);
        rawB0 =  A * ((A + 1.0f) + (A - 1.0f) * cosW0 + 2.0f * sqrtA * alphaS);
        rawB1 = -2.0f * A * ((A - 1.0f) + (A + 1.0f) * cosW0);
        rawB2 =  A * ((A + 1.0f) + (A - 1.0f) * cosW0 - 2.0f * sqrtA * alphaS);
        rawA0 =       (A + 1.0f) - (A - 1.0f) * cosW0 + 2.0f * sqrtA * alphaS;
        rawA1 =  2.0f * ((A - 1.0f) - (A + 1.0f) * cosW0);
        rawA2 =       (A + 1.0f) - (A - 1.0f) * cosW0 - 2.0f * sqrtA * alphaS;
    }

    c.b0 = rawB0 / rawA0;
    c.b1 = rawB1 / rawA0;
    c.b2 = rawB2 / rawA0;
    c.a1 = rawA1 / rawA0;
    c.a2 = rawA2 / rawA0;

    // Reset filter state to avoid transient on coefficient change
    eqStateL[bandIdx] = {0.0f, 0.0f, 0.0f, 0.0f};
    eqStateR[bandIdx] = {0.0f, 0.0f, 0.0f, 0.0f};
}

static inline float processBiquad(const BiquadCoeffs &c, BiquadState &s, float in)
{
    float out = c.b0 * in + c.b1 * s.x1 + c.b2 * s.x2 - c.a1 * s.y1 - c.a2 * s.y2;
    s.x2 = s.x1;  s.x1 = in;
    s.y2 = s.y1;  s.y1 = out;
    // Anti-denormal
    if (s.y1 > -1e-18f && s.y1 < 1e-18f) s.y1 = 0.0f;
    if (s.y2 > -1e-18f && s.y2 < 1e-18f) s.y2 = 0.0f;
    return out;
}

static void applyEq(int16_t *data, int32_t numFrames, int32_t numChannels)
{
    if (!eqEnabled) return;

    for (int32_t frame = 0; frame < numFrames; frame++)
    {
        float l = data[frame * numChannels]     / 32768.0f;
        float r = (numChannels > 1) ? data[frame * numChannels + 1] / 32768.0f : l;

        for (int band = 0; band < EQ_BANDS; band++)
        {
            if (eqCoeffs[band].bypassed) continue;
            l = processBiquad(eqCoeffs[band], eqStateL[band], l);
            r = processBiquad(eqCoeffs[band], eqStateR[band], r);
        }

        // Clip and write back
        data[frame * numChannels] = (int16_t)(l < -1.0f ? -32768 : l > 1.0f ? 32767 : l * 32767.0f);
        if (numChannels > 1)
            data[frame * numChannels + 1] = (int16_t)(r < -1.0f ? -32768 : r > 1.0f ? 32767 : r * 32767.0f);
    }
}

// EAS data
static EAS_DATA_HANDLE pEASData;
static EAS_I32 bufferSize;
static EAS_HANDLE midiHandle;
static int isDLSLoaded;

// Functions
oboe::Result initOboe();
oboe::Result closeOboe();

// oboe callback
class OboeCallback: public oboe::AudioStreamDataCallback
{
public:
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *audioStream,
                                          void *audioData, int32_t numFrames)
    {
        EAS_RESULT result;
        EAS_I32 numGenerated;
        EAS_I32 count = 0;

        // We requested AudioFormat::I16. So if the stream opens
        // we know we got the I16 format.
        auto *outputData = static_cast<int16_t *>(audioData);

        while (count < bufferSize)
        {
            // lock
            pthread_mutex_lock(&mutex);

            result = EAS_Render(pEASData, outputData + count,
                                pLibConfig->mixBufferSize, &numGenerated);
            // unlock
            pthread_mutex_unlock(&mutex);

            assert(result == EAS_SUCCESS);

            count += numGenerated * pLibConfig->numChannels;
        }

        // Apply 10-band EQ post-render
        applyEq(outputData, bufferSize / pLibConfig->numChannels, pLibConfig->numChannels);

        return oboe::DataCallbackResult::Continue;
    }

    void onErrorAfterClose(oboe::AudioStream *audioStream, oboe::Result error)
    {
        if (error ==  oboe::Result::ErrorDisconnected)
            initOboe();
    }
};

// oboe callback
OboeCallback oboeCallback;

// build oboe
oboe::Result buildOboe()
{
    oboe::AudioStreamBuilder builder;

    builder.setDirection(oboe::Direction::Output);
    builder.setPerformanceMode(oboe::PerformanceMode::LowLatency);
    builder.setSharingMode(oboe::SharingMode::Exclusive);
    builder.setFormat(oboe::AudioFormat::I16);
    builder.setFramesPerCallback(bufferSize / pLibConfig->numChannels);
    builder.setChannelCount(pLibConfig->numChannels);
    builder.setSampleRate(pLibConfig->sampleRate);
    builder.setDataCallback(&oboeCallback);

    // Set preferred output device if specified
    if (preferredDeviceId != oboe::kUnspecified)
    {
        builder.setDeviceId(preferredDeviceId);
        LOG_D(LOG_TAG, "Using preferred output device ID: %d", preferredDeviceId);
    }

    return builder.openStream(oboeStream);
}

oboe::Result initOboe()
{
    oboe::Result oboeResult;

    if ((oboeResult = buildOboe()) != oboe::Result::OK)
    {
        LOG_E(LOG_TAG, "Failed to create oboe stream. Error: %s",
              oboe::convertToText(oboeResult));

        return oboeResult;
    }

    if ((oboeResult = oboeStream->requestStart()) != oboe::Result::OK)
    {
        closeOboe();

        LOG_E(LOG_TAG, "Failed to start oboe stream. Error: %s",
              oboe::convertToText(oboeResult));

        return oboeResult;
    }

    return oboe::Result::OK;
}

// close oboe
oboe::Result closeOboe()
{
    if (oboeStream != NULL)
    {
        oboeStream->requestStop();
        return oboeStream->close();
    }

    return oboe::Result::ErrorNull;
}

// init EAS midi
EAS_RESULT initEAS()
{
    EAS_RESULT result;

    // get the library configuration
    pLibConfig = EAS_Config();
    if (pLibConfig == NULL || pLibConfig->libVersion != LIB_VERSION)
        return EAS_FAILURE;

    // calculate buffer size
    bufferSize = pLibConfig->mixBufferSize * pLibConfig->numChannels * NUM_BUFFERS;

    // init library
    if ((result = EAS_Init(&pEASData)) != EAS_SUCCESS)
        return result;

    // select reverb preset and enable
    EAS_SetParameter(pEASData, EAS_MODULE_REVERB, EAS_PARAM_REVERB_PRESET,
                     EAS_PARAM_REVERB_CHAMBER);
    EAS_SetParameter(pEASData, EAS_MODULE_REVERB, EAS_PARAM_REVERB_BYPASS,
                     EAS_FALSE);

    // open midi stream
    if ((result = EAS_OpenMIDIStream(pEASData, &midiHandle, NULL)) != EAS_SUCCESS)
        return result;

    isDLSLoaded = 0;

    return EAS_SUCCESS;
}

// shutdown EAS midi
void shutdownEAS()
{

    if (midiHandle != NULL)
    {
        EAS_CloseMIDIStream(pEASData, midiHandle);
        midiHandle = NULL;
    }

    if (pEASData != NULL)
    {
        EAS_Shutdown(pEASData);
        pEASData = NULL;
    }

    isDLSLoaded = 0;
}

// init mididriver
jboolean midi_init()
{
    EAS_RESULT result;
    oboe::Result oboeResult;

    if ((result = initEAS()) != EAS_SUCCESS)
    {
        shutdownEAS();

        LOG_E(LOG_TAG, "Init EAS failed: %ld", result);

        return JNI_FALSE;
    }

    // LOG_D(LOG_TAG, "Init EAS success, buffer: %ld", bufferSize);

    if ((oboeResult = initOboe()) != oboe::Result::OK)
    {
        shutdownEAS();

        return JNI_FALSE;
    }

    // Reset EQ filter states (sample rate may have changed after EAS init)
    initEqCoeffs();

    return JNI_TRUE;
}

jboolean
Java_org_billthefarmer_mididriver_MidiDriver_init(JNIEnv *env,
                                                  jobject obj)
{
    return midi_init();
}

// midi config
jintArray
Java_org_billthefarmer_mididriver_MidiDriver_config(JNIEnv *env,
                                                    jobject obj)
{
    jboolean isCopy;

    if (pLibConfig == NULL)
        return NULL;

    jintArray configArray = env->NewIntArray(4);

    jint *config = env->GetIntArrayElements(configArray, &isCopy);

    config[0] = pLibConfig->maxVoices;
    config[1] = pLibConfig->numChannels;
    config[2] = pLibConfig->sampleRate;
    config[3] = pLibConfig->mixBufferSize;

    env->ReleaseIntArrayElements(configArray, config, 0);

    return configArray;
}

// midi write
jboolean midi_write(EAS_U8 *bytes, jint length)
{
    EAS_RESULT result;

    if (pEASData == NULL || midiHandle == NULL)
        return JNI_FALSE;

    // lock
    pthread_mutex_lock(&mutex);

    result = EAS_WriteMIDIStream(pEASData, midiHandle, bytes, length);

    // unlock
    pthread_mutex_unlock(&mutex);

    if (result != EAS_SUCCESS)
        return JNI_FALSE;

    return JNI_TRUE;
}

jboolean
Java_org_billthefarmer_mididriver_MidiDriver_write(JNIEnv *env,
                                                   jobject obj,
                                                   jbyteArray byteArray)
{
    jboolean result;
    jboolean isCopy;
    jint length;
    EAS_U8 *bytes;

    bytes = (EAS_U8 *) env->GetByteArrayElements(byteArray, &isCopy);
    length = env->GetArrayLength(byteArray);

    result = midi_write(bytes, length);

    env->ReleaseByteArrayElements(byteArray, (jbyte *) bytes, 0);

    return result;
}

// set EAS master volume
jboolean midi_setVolume(jint volume)
{
    EAS_RESULT result;

    if (pEASData == NULL || midiHandle == NULL)
        return JNI_FALSE;

    result = EAS_SetVolume(pEASData, NULL, (EAS_I32) volume);

    if (result != EAS_SUCCESS)
        return JNI_FALSE;

    return JNI_TRUE;
}

jboolean
Java_org_billthefarmer_mididriver_MidiDriver_setVolume(JNIEnv *env,
                                                       jobject obj,
                                                       jint volume)
{
    return midi_setVolume(volume);
}

// Set EAS reverb
jboolean midi_setReverb(jint preset)
{
    EAS_RESULT result;

    if (preset >= 0)
    {
        result = EAS_SetParameter(pEASData, EAS_MODULE_REVERB,
                                  EAS_PARAM_REVERB_PRESET, preset);
        if (result != EAS_SUCCESS)
        {
            LOG_E(LOG_TAG, "Set EAS reverb preset failed: %ld", result);
            return JNI_FALSE;
        }

        result = EAS_SetParameter(pEASData, EAS_MODULE_REVERB,
                                  EAS_PARAM_REVERB_BYPASS, EAS_FALSE);
        if (result != EAS_SUCCESS)
        {
            LOG_E(LOG_TAG, "Enable EAS reverb failed: %ld", result);
            return JNI_FALSE;
        }
    }

    else
    {
        result = EAS_SetParameter(pEASData, EAS_MODULE_REVERB,
                                  EAS_PARAM_REVERB_BYPASS, EAS_TRUE);
        if (result != EAS_SUCCESS)
        {
            LOG_E(LOG_TAG, "Disable EAS reverb failed: %ld", result);
            return JNI_FALSE;
        }
    }

    return JNI_TRUE;
}

jboolean
Java_org_billthefarmer_mididriver_MidiDriver_setReverb(JNIEnv *env,
                                                       jobject obj,
                                                       jint preset)
{
    return midi_setReverb(preset);
}

// set output device
// deviceId: device ID from AudioDeviceInfo, or -1 for default
// Returns true if stream was successfully restarted with new device
jboolean midi_setOutputDevice(jint deviceId)
{
    oboe::Result oboeResult;

    LOG_D(LOG_TAG, "Setting output device to: %d", deviceId);

    // If stream is running, restart it with the new device
    if (oboeStream != NULL && pEASData != NULL)
    {
        // Close current stream
        closeOboe();

        // Store the preferred device and try to reopen
        preferredDeviceId = deviceId;

        // Reopen with new device
        if ((oboeResult = initOboe()) != oboe::Result::OK)
        {
            LOG_E(LOG_TAG, "Failed to restart oboe with device %d: %s",
                  deviceId, oboe::convertToText(oboeResult));

            // Fallback: try to reopen with default device to avoid no audio
            LOG_W(LOG_TAG, "Falling back to default audio device");
            preferredDeviceId = oboe::kUnspecified;

            if ((oboeResult = initOboe()) != oboe::Result::OK)
            {
                LOG_E(LOG_TAG, "Failed to restart oboe with default device: %s",
                      oboe::convertToText(oboeResult));
                return JNI_FALSE;
            }

            LOG_D(LOG_TAG, "Fallback to default device succeeded");
            return JNI_FALSE; // Return false because we didn't use the requested device
        }

        LOG_D(LOG_TAG, "Successfully switched to output device: %d", deviceId);
    }
    else
    {
        // Stream not running yet, just store the preference for next init
        preferredDeviceId = deviceId;
    }

    return JNI_TRUE;
}

jboolean
Java_org_billthefarmer_mididriver_MidiDriver_setOutputDevice(JNIEnv *env,
                                                              jobject obj,
                                                              jint deviceId)
{
    return midi_setOutputDevice(deviceId);
}

// shutdown EAS midi
jboolean midi_shutdown()
{
    closeOboe();
    shutdownEAS();

    // Reset preferred device
    preferredDeviceId = oboe::kUnspecified;

    return JNI_TRUE;
}

jboolean
Java_org_billthefarmer_mididriver_MidiDriver_shutdown(JNIEnv *env,
                                                      jobject obj)
{
    return midi_shutdown();
}

static int memDLS_readAt(void *handle, void *buf, int offset, int size)
{
    const EAS_U8 *data;
    EAS_DLS_HANDLE *pHandle;

    pHandle = (EAS_DLS_HANDLE *) handle;
    data = pHandle->data;
    EAS_HWMemCpy(buf, data + offset, size);

    return size;
}

static int memDLS_size(void *handle)
{
    EAS_DLS_HANDLE *pHandle;

    pHandle = (EAS_DLS_HANDLE *) handle;
    return pHandle->len;
}

jboolean midi_loadDLS(const EAS_U8 *dlsData, jint length)
{
    EAS_FILE file;
    EAS_RESULT result;
    EAS_DLS_HANDLE handle = { length, dlsData };

    file.handle = (void *) &handle;
    file.readAt = memDLS_readAt;
    file.size = memDLS_size;

    result = EAS_LoadDLSCollection(pEASData, midiHandle, &file);
    if (result != EAS_SUCCESS)
        return JNI_FALSE;

    isDLSLoaded = 1;
    return JNI_TRUE;
}

jboolean
Java_org_billthefarmer_mididriver_MidiDriver_loadDLS(JNIEnv *env,
                                                     jobject obj,
                                                     jbyteArray byteArray)
{
    jint length;
    EAS_U8 *bytes;
    jboolean isCopy;
    jboolean result;

    if (isDLSLoaded != 0)
        return JNI_FALSE;

    bytes = (EAS_U8 *) env->GetByteArrayElements(byteArray, &isCopy);
    length = env->GetArrayLength(byteArray);

    result = midi_loadDLS(bytes, length);

    env->ReleaseByteArrayElements(byteArray, (jbyte *) bytes, 0);

    return result;
}

// Enable or disable the 10-band EQ
jboolean
Java_org_billthefarmer_mididriver_MidiDriver_setEqEnabled(JNIEnv *env,
                                                          jobject obj,
                                                          jboolean enabled)
{
    eqEnabled = (enabled == JNI_TRUE);
    if (eqEnabled)
    {
        // Reset all filter states when enabling to prevent transient artifacts
        for (int i = 0; i < EQ_BANDS; i++)
        {
            eqStateL[i] = {0.0f, 0.0f, 0.0f, 0.0f};
            eqStateR[i] = {0.0f, 0.0f, 0.0f, 0.0f};
        }
    }
    return JNI_TRUE;
}

// Set gain for one EQ band (gainDb in dB, ±12 dB range)
jboolean
Java_org_billthefarmer_mididriver_MidiDriver_setEqBandGain(JNIEnv *env,
                                                           jobject obj,
                                                           jint  band,
                                                           jfloat gainDb)
{
    computeEqCoeffs((int)band, (float)gainDb);
    return JNI_TRUE;
}
