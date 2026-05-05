#include "SDL_limboaudio.h"

static int enable_aaudio;

JNIEXPORT void JNICALL Java_com_max2idea_android_limbo_jni_VMExecutor_nativeEnableAaudio(
        JNIEnv* env,
        jobject thiz,
        jint value,
        jstring aaudioLibName,
        jstring aaudioLibFullpath) {
    (void) env;
    (void) thiz;
    (void) aaudioLibName;
    (void) aaudioLibFullpath;
    enable_aaudio = value != 0;
}

int isAaudioEnabled(void) {
    return enable_aaudio;
}
