#include "webrtc/common_audio/vad/include/webrtc_vad.h"
#include "webrtc/common_audio/signal_processing/include/signal_processing_library.h"
#include <stdlib.h>
#include "webrtc/common_audio/include/typedefs.h"
#include <jni.h>

#define AGGRESSIVENESS 3
VadInst* internalHandle;
int resultVad;

JNIEXPORT jint JNICALL Java_com_mozilla_speechlibrary_Vad_start (JNIEnv * env, jobject obj){
    int ret_state = 0;
    internalHandle = NULL;

    ret_state = WebRtcVad_Create(&internalHandle);
    if (ret_state == -1) return -1;
    ret_state =  WebRtcVad_Init(internalHandle);
    if (ret_state == -1) return -2;
    ret_state = WebRtcVad_set_mode(internalHandle, AGGRESSIVENESS);
    if (ret_state == -1) return -3;
    return ret_state;
}

JNIEXPORT jint JNICALL Java_com_mozilla_speechlibrary_Vad_stop(JNIEnv * env, jobject object) {
    if (WebRtcVad_Free(internalHandle) == 0) {
        internalHandle = NULL;
    }
}

JNIEXPORT jint JNICALL Java_com_mozilla_speechlibrary_Vad_isSilence(JNIEnv * env, jobject object) {
    return resultVad;
}

JNIEXPORT jint JNICALL Java_com_mozilla_speechlibrary_Vad_feed(JNIEnv * env, jobject object, jshortArray bytes, jint size) {
    jshort *arrayElements = (*env)->GetShortArrayElements(env, bytes, 0);
    resultVad = WebRtcVad_Process(internalHandle, 16000, arrayElements, size);
    (*env)->ReleaseShortArrayElements(env, bytes, arrayElements, 0);
    return resultVad;
}