/*
Copyright (C) Max Kastanas 2012

 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 */

#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <inttypes.h>
#include <math.h>
#include <semaphore.h>
#include <dlfcn.h>
#include <aaudio/AAudio.h>
#include "SDL_limboaudio.h"

static void *aaudioLibHandle = NULL;
static int aaudioSymbolsLoaded = 0;

static aaudio_result_t (*limbo_AAudio_createStreamBuilder)(AAudioStreamBuilder **builder);
static const char *(*limbo_AAudio_convertResultToText)(aaudio_result_t returnCode);
static void (*limbo_AAudioStreamBuilder_setSampleRate)(AAudioStreamBuilder *builder, int32_t sampleRate);
static void (*limbo_AAudioStreamBuilder_setChannelCount)(AAudioStreamBuilder *builder, int32_t channelCount);
static void (*limbo_AAudioStreamBuilder_setFormat)(AAudioStreamBuilder *builder, aaudio_format_t format);
static void (*limbo_AAudioStreamBuilder_setPerformanceMode)(
        AAudioStreamBuilder *builder,
        aaudio_performance_mode_t performanceMode);
static void (*limbo_AAudioStreamBuilder_setDataCallback)(
        AAudioStreamBuilder *builder,
        AAudioStream_dataCallback callback,
        void *userData);
static aaudio_result_t (*limbo_AAudioStreamBuilder_openStream)(
        AAudioStreamBuilder *builder,
        AAudioStream **stream);
static aaudio_result_t (*limbo_AAudioStreamBuilder_delete)(AAudioStreamBuilder *builder);
static int32_t (*limbo_AAudioStream_getDeviceId)(AAudioStream *stream);
static aaudio_direction_t (*limbo_AAudioStream_getDirection)(AAudioStream *stream);
static aaudio_sharing_mode_t (*limbo_AAudioStream_getSharingMode)(AAudioStream *stream);
static int32_t (*limbo_AAudioStream_getSampleRate)(AAudioStream *stream);
static int32_t (*limbo_AAudioStream_getChannelCount)(AAudioStream *stream);
static int32_t (*limbo_AAudioStream_getFramesPerBurst)(AAudioStream *stream);
static aaudio_format_t (*limbo_AAudioStream_getFormat)(AAudioStream *stream);
static int32_t (*limbo_AAudioStream_getBufferCapacityInFrames)(AAudioStream *stream);
static aaudio_result_t (*limbo_AAudioStream_requestStart)(AAudioStream *stream);
static aaudio_result_t (*limbo_AAudioStream_requestStop)(AAudioStream *stream);
static aaudio_result_t (*limbo_AAudioStream_close)(AAudioStream *stream);
static aaudio_result_t (*limbo_AAudioStream_write)(
        AAudioStream *stream,
        const void *buffer,
        int32_t numFrames,
        int64_t timeoutNanoseconds);

#define LIMBO_LOAD_AAUDIO_SYMBOL(symbol) \
    do { \
        limbo_##symbol = dlsym(aaudioLibHandle, #symbol); \
        if (limbo_##symbol == NULL) { \
            printf("Unable to load AAudio symbol: %s\n", #symbol); \
            return 0; \
        } \
    } while (0)

static int loadAAudioSymbols(void) {
    if (aaudioSymbolsLoaded) {
        return 1;
    }
    aaudioLibHandle = dlopen("libaaudio.so", RTLD_NOW | RTLD_LOCAL);
    if (aaudioLibHandle == NULL) {
        printf("AAudio is not available: %s\n", dlerror());
        return 0;
    }

    LIMBO_LOAD_AAUDIO_SYMBOL(AAudio_createStreamBuilder);
    LIMBO_LOAD_AAUDIO_SYMBOL(AAudio_convertResultToText);
    LIMBO_LOAD_AAUDIO_SYMBOL(AAudioStreamBuilder_setSampleRate);
    LIMBO_LOAD_AAUDIO_SYMBOL(AAudioStreamBuilder_setChannelCount);
    LIMBO_LOAD_AAUDIO_SYMBOL(AAudioStreamBuilder_setFormat);
    LIMBO_LOAD_AAUDIO_SYMBOL(AAudioStreamBuilder_setPerformanceMode);
    LIMBO_LOAD_AAUDIO_SYMBOL(AAudioStreamBuilder_setDataCallback);
    LIMBO_LOAD_AAUDIO_SYMBOL(AAudioStreamBuilder_openStream);
    LIMBO_LOAD_AAUDIO_SYMBOL(AAudioStreamBuilder_delete);
    LIMBO_LOAD_AAUDIO_SYMBOL(AAudioStream_getDeviceId);
    LIMBO_LOAD_AAUDIO_SYMBOL(AAudioStream_getDirection);
    LIMBO_LOAD_AAUDIO_SYMBOL(AAudioStream_getSharingMode);
    LIMBO_LOAD_AAUDIO_SYMBOL(AAudioStream_getSampleRate);
    LIMBO_LOAD_AAUDIO_SYMBOL(AAudioStream_getChannelCount);
    LIMBO_LOAD_AAUDIO_SYMBOL(AAudioStream_getFramesPerBurst);
    LIMBO_LOAD_AAUDIO_SYMBOL(AAudioStream_getFormat);
    LIMBO_LOAD_AAUDIO_SYMBOL(AAudioStream_getBufferCapacityInFrames);
    LIMBO_LOAD_AAUDIO_SYMBOL(AAudioStream_requestStart);
    LIMBO_LOAD_AAUDIO_SYMBOL(AAudioStream_requestStop);
    LIMBO_LOAD_AAUDIO_SYMBOL(AAudioStream_close);
    LIMBO_LOAD_AAUDIO_SYMBOL(AAudioStream_write);

    aaudioSymbolsLoaded = 1;
    return 1;
}

// currently no need to resample unless we have a sample rate 
// that aaudio cannot handle, if so we prefer 22050
int enableAaudioResample = 0;
//float aaudioResampleRate = 11025.0;
float aaudioResampleRate = 22050.0;
// we drop the frames you can use 0 to do a median filter instead
int aaudioDropFrames = 1;

// FIXME: buggy
int enableAaudioHighPriority = 0;

// LIMBO: we use aaudio to remove the JNI calls to AudioTrack
// We also try to use a lower sample rate for better performance
// Make sure you update: 
// the freq in qemu driver located in qemu/audio/sdlaudio.c with 22050
// and optionally the buffer frames in qemu/audio/audio.c
AAudioStreamBuilder *builder;
AAudioStream *stream;
int aaudioBurstFrames;
int aaudioCapacityFrames;
int aaudioFrames;
int aaudioChannels;

// high priority aaudio callback
int aaudioBufferStart = 0;
int aaudioBufferEnd = 0;
int aaudioBufferSize = 0;
int aaudioMidBufferSize = 0;

short * aaudioMidBuffer = NULL;
short * aaudioBuffer = NULL;

int aaudioResampleStep = 0;
int aaudioResampleFrames = 0;
int aaudioMutexInitialized = 0;

sem_t mutex;

// FIXME: this is buggy, though since the aaudio write function is
// fast enough we don't bother for now
aaudio_data_callback_result_t aaudio_callback(
        AAudioStream *stream,
        void *userData,
        void *audioData,
        int32_t numFrames) {
        	sem_wait(&mutex);
    /*	
	printf("bytes writting, numFrames = %d, aaudioBufferSize = %d "
	",aaudioBufferStart = %d, aaudioBufferEnd = %d\n", numFrames, "
    "aaudioBufferSize, aaudioBufferStart, aaudioBufferEnd);
    */				
	int bytesWritten = 0;
    for(int i=0; i<numFrames*aaudioChannels; i++) {
    	if(aaudioBufferStart >= aaudioBufferSize)
    		aaudioBufferStart = 0;
    	if(aaudioBufferStart < aaudioBufferEnd) {
    		((short*)audioData)[i] = aaudioBuffer[aaudioBufferStart++];
    		bytesWritten++;
    	} else {
	    	break;
    	}
    }
    /*
    printf("bytes written, bytesWritten = %d, aaudioBufferSize = %d"
    ",aaudioBufferStart = %d, aaudioBufferEnd = %d\n", "
    "bytesWritten, aaudioBufferSize, aaudioBufferStart, aaudioBufferEnd);
    */				
    sem_post(&mutex);
    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}
     
void createAAudioDevice(int sampleRate, int channelCount, int desiredBufferFrames){	
    if(!loadAAudioSymbols()) {
        return;
    }
    // Does this prevent the vm from crashing with a stackoverflowerror?
	sleep(1);
	aaudio_result_t res = limbo_AAudio_createStreamBuilder(&builder);
	if(res != AAUDIO_OK){
		printf("Error while creating builder: %s\n", limbo_AAudio_convertResultToText(res));
        destroyAaudioDevice();
        return;
	}
    if(enableAaudioResample)
        printf("requested resampling rate: %f\n", aaudioResampleRate);
    else
        printf("requested sampling rate: %d\n", sampleRate);
	limbo_AAudioStreamBuilder_setSampleRate(builder, enableAaudioResample?aaudioResampleRate:sampleRate);
	limbo_AAudioStreamBuilder_setChannelCount(builder, channelCount);
	limbo_AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_I16);
	//AAudioStreamBuilder_setBufferCapacityInFrames(builder, desiredBufferFrames);
	limbo_AAudioStreamBuilder_setPerformanceMode(builder,AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
	if(enableAaudioHighPriority)
		limbo_AAudioStreamBuilder_setDataCallback(builder, aaudio_callback, aaudioBuffer);

	res = limbo_AAudioStreamBuilder_openStream(builder, &stream);
	if(res != AAUDIO_OK){
		printf("Error while opening stream: %s\n", limbo_AAudio_convertResultToText(res));
        destroyAaudioDevice();
        return;
	}
	
	printf("Stream deviceId: %d\n", limbo_AAudioStream_getDeviceId(stream));
	printf("Stream direction: %d\n", limbo_AAudioStream_getDirection(stream));
	
	aaudio_sharing_mode_t sharingMode = limbo_AAudioStream_getSharingMode(stream);
	if(sharingMode != AAUDIO_SHARING_MODE_SHARED)	
		printf("Stream sharingMode invalid: %d\n", sharingMode);
		
    aaudioResampleRate = limbo_AAudioStream_getSampleRate(stream);
	printf("Stream sampleRate: %f\n", aaudioResampleRate);
	aaudioChannels = limbo_AAudioStream_getChannelCount(stream);
	printf("Stream channelCount: %d\n", aaudioChannels);

	aaudioBurstFrames = limbo_AAudioStream_getFramesPerBurst(stream);
	printf("Got optimal numFrames: %d\n", aaudioBurstFrames);
	
	aaudio_format_t dataFormat = limbo_AAudioStream_getFormat(stream);
	if (dataFormat != AAUDIO_FORMAT_PCM_I16) {
    	printf("Stream format invalid: %d\n", dataFormat);	
	}
	
	aaudioCapacityFrames = limbo_AAudioStream_getBufferCapacityInFrames(stream);
	printf("Stream frames capacity: %d\n", aaudioCapacityFrames);
	
    //TODO: we could use the optimal number of frames
    // only if supported by SDL
	// aaudioFrames = aaudioBurstFrames;
	aaudioFrames = desiredBufferFrames;
	
    if(sem_init(&mutex, 0, 1) == 0) {
        aaudioMutexInitialized = 1;
    }
    printf("Samples: %d\n", aaudioFrames);
    printf("Channels: %d\n", aaudioChannels);
    
    // setup resampling
    if(enableAaudioResample) {
        printf("final resampling rate: %f\n", aaudioResampleRate);
        aaudioFrames = ceil(aaudioFrames * aaudioResampleRate / sampleRate);
        printf("resampled frames: %d\n", aaudioFrames);
        aaudioResampleStep = sampleRate / aaudioResampleRate;
        printf("resample step: %d\n", aaudioResampleStep);
    }
    
    aaudioBufferSize = aaudioFrames * aaudioChannels;
    
    //TODO: high priority callback
    // we allocate 5 times more capacity to be safe
    if(enableAaudioHighPriority) {
        aaudioBufferSize = aaudioBufferSize * 5;
    }
    // This buffer is for receiving the data
    aaudioMidBufferSize = desiredBufferFrames * aaudioChannels;
    aaudioMidBuffer = (short*) calloc(aaudioMidBufferSize, sizeof(short));
    
    // This is for resampling
    aaudioBuffer = (short*) calloc(aaudioBufferSize, sizeof(short));
            
    printf("aaudio final frames: %d\n", aaudioFrames);
    printf("aaudio final buffer size: %d\n", aaudioBufferSize);
        
	res = limbo_AAudioStream_requestStart(stream);
	if(res != AAUDIO_OK){
		printf("Error while starting stream: %d\n", res);	
	}
	printf("Started playing\n");
}

void destroyAaudioDevice() {
    if(stream != NULL) {
        if(aaudioSymbolsLoaded) {
            limbo_AAudioStream_requestStop(stream);
            limbo_AAudioStream_close(stream);
        }
        stream = NULL;
    }
    if(builder != NULL) {
        if(aaudioSymbolsLoaded) {
            limbo_AAudioStreamBuilder_delete(builder);
        }
        builder = NULL;
    }
    free(aaudioMidBuffer);
    aaudioMidBuffer = NULL;
    free(aaudioBuffer);
    aaudioBuffer = NULL;
    aaudioBufferStart = 0;
    aaudioBufferEnd = 0;
    aaudioBufferSize = 0;
    aaudioMidBufferSize = 0;
    if(aaudioMutexInitialized) {
        sem_destroy(&mutex);
        aaudioMutexInitialized = 0;
    }
}

int isAaudioBufferEmpty() {
    for(int i=0; i<aaudioMidBufferSize; i++){
        if(aaudioMidBuffer[i] != 0)
            return 0;
    }
    return 1;
}

int batchCount = 0;
void resampleAaudio() {
    int batch = batchCount++;
    //printf("resampling batch: %d, step: %d, bufferSize: %d\n", batch, aaudioResampleStep, aaudioBufferSize);
    memset(aaudioBuffer, 0, aaudioBufferSize * sizeof(short));
    int sum = 0; 
    int oldFrame = 0;
    int sampleCount = aaudioDropFrames?1:aaudioResampleStep;
    for (int frame = 0; frame < aaudioBufferSize; frame+=aaudioChannels) {
        int oldPos = oldFrame;
        for (int channel = 0; channel < aaudioChannels; channel++) {
            sum = 0;
            for (int sample = 0; sample < aaudioResampleStep*aaudioChannels; sample+=aaudioChannels) {
                if(!aaudioDropFrames || sample == 0)
                    sum += aaudioMidBuffer[oldPos+sample];
                if(aaudioDropFrames)
                    break;
      //          printf("batch %d, adding amb[%d] = %d => sum = %d\n", batch,
      //              oldPos+sample, aaudioMidBuffer[oldPos+sample], sum);
            }
            aaudioBuffer[frame + channel] = sum / sampleCount;
            //printf("batch %d, set avg ab[%d] <= %d\n", batch,
                    //frame + channel, sum / sampleCount);
            oldPos++;
        }
        oldFrame += aaudioChannels * aaudioResampleStep ;
    }
}

void writeAaudio() {
    if(aaudioMidBuffer == NULL) {
            return;
        }
        if (enableAaudioHighPriority) {
            writeAaudioQueue();
        } else {
            writeAaudioStream();
        }
}

void writeAaudioStream() {
    if(aaudioMidBuffer == NULL)
        return;
    int index = 0;
    int res = 0;
    // FIXME: Unfortunately we need to block here for at least 1 ms so 
    // sdl and qemu will feed us new data. Perhaps there is an issue 
    // in sdl or qemu.
    if(enableAaudioResample) {
        resampleAaudio();
        res = limbo_AAudioStream_write(stream, (short*) aaudioBuffer, aaudioFrames, 1);
    } else {
        res = limbo_AAudioStream_write(stream, (short*) aaudioMidBuffer, aaudioFrames, 1);
    }
    if(res < 0) {
        printf("Error writting: %s\n", limbo_AAudio_convertResultToText(res));
    } else {
        // printf("Frames written: %d\n", res);
    }
}

void writeAaudioQueue() {
    // LIMBO: we qeueue the data to our cyclical buffer 
    // which is used by a high priority aaudio callback
    // that consumes the data
    // NEEDS TESTING
    if(aaudioMidBuffer == NULL) {
        printf("aaudio stream not ready\n");
        return;
    }
    sem_wait(&mutex);
    /*
    printf("bytes reading from buffer, aaudioFrames = %d, aaudioBufferSize = %d "
    ", aaudioBufferStart = %d, aaudioBufferEnd = %d\n",
        aaudioFrames, aaudioBufferSize, aaudioBufferStart, aaudioBufferEnd				 
      );
    */				
    // TODO: resample and copy at the same time?
    resampleAaudio();
    int bytesRead = 0;
    for(int i=0; i<aaudioFrames * aaudioChannels; i++) {
        if(aaudioBufferEnd >= aaudioBufferSize) {
            //printf("wrapping around aaudioBufferEnd\n");
            aaudioBufferEnd = 0;
        }
        //printf("reading aaudioBufferEnd = %d\n" , aaudioBufferEnd);
        aaudioBuffer[aaudioBufferEnd++] = ((short*)  aaudioBuffer)[i];
        bytesRead++;
        }
        // artificially wait
        usleep(7 * 1000);
     /*
     printf("bytes wrote to queue, bytesRead = %d, aaudioBufferSize = %d " 
     ", aaudioBufferStart = %d, aaudioBufferEnd = %d\n",
        bytesRead, aaudioBufferSize, aaudioBufferStart, aaudioBufferEnd);
        */
    
    sem_post(&mutex);
}

void* getAaudioBuffer() {
    return aaudioMidBuffer;
}
