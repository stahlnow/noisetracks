#include <jni.h>
#include <stdio.h>
#include <errno.h>
#include "ivorbisfile.h"

///////////////////////////////////////////////// VorbisDecoder

class VorbisDecoder {
public:
	VorbisDecoder();
	~VorbisDecoder();
	int Open(const char*);
	void Close();
	bool IsSeekable();
	int GetRate();
	int GetChannels();
	int GetTimeLength();
	int GetTimePosition();
	int SeekToTime(int time);
	int Read(jbyte * buffer,int length);
	int Read(jshort * buffer,int length);
private:
	VorbisDecoder(const VorbisDecoder&);
	VorbisDecoder & operator=(const VorbisDecoder&);
private:
	bool m_opened;
	OggVorbis_File m_file;
	int m_bitstream;
};

VorbisDecoder::VorbisDecoder():
	m_opened(false)
{
}

VorbisDecoder::~VorbisDecoder() {
	Close();
}

int VorbisDecoder::Open(const char * path) {
	Close();
	FILE * file=fopen(path,"r");
	if (!file) {
		return errno;
	}
	int error=ov_open(file,&m_file,0,0);
	if (error!=0) {
		fclose(file);
		return error;
	}
	m_opened=true;
	m_bitstream=0;
	return 0;
}

void VorbisDecoder::Close() {
	if (m_opened) {
		ov_clear(&m_file);
		m_opened=false;
	}
}

bool VorbisDecoder::IsSeekable() {
	if (!m_opened) {
		return false;
	}
	return ov_seekable(&m_file)!=0;
}

int VorbisDecoder::GetRate() {
	if (!m_opened) {
		return 0;
	}
	vorbis_info * info=ov_info(&m_file,-1);
	if (!info) {
		return 0;
	}
	return info->rate;
}

int VorbisDecoder::GetChannels() {
	if (!m_opened) {
		return 0;
	}
	vorbis_info * info=ov_info(&m_file,-1);
	if (!info) {
		return 0;
	}
	return info->channels;
}

int VorbisDecoder::GetTimeLength() {
	if (!m_opened) {
		return 0;
	}
	return int(ov_time_total(&m_file,-1));
}

int VorbisDecoder::GetTimePosition() {
	if (!m_opened) {
		return 0;
	}
	return int(ov_time_tell(&m_file));
}	

int VorbisDecoder::SeekToTime(int time) {
	if (!m_opened) {
		return OV_FALSE;
	}
	return ov_time_seek(&m_file,time);
}

int VorbisDecoder::Read(jbyte * buffer,int length) {
	if (!m_opened) {
		return 0;
	}
	return ov_read(&m_file,(char*)buffer,length,&m_bitstream);
}

///////////////////////////////////////////////// JNI

static jfieldID gNativeInstanceFieldID;

static VorbisDecoder * getInstance(JNIEnv * env,jobject object) {
	return (VorbisDecoder*)env->GetIntField(object,gNativeInstanceFieldID);
}

static void setInstance(JNIEnv * env,jobject object,VorbisDecoder * instance) {
	env->SetIntField(object,gNativeInstanceFieldID,(int)instance);
}

extern "C" void
Java_com_noisetracks_android_audio_VorbisDecoder_nativeStaticSetup(JNIEnv * env) {
	jclass clazz=env->FindClass("com/noisetracks/android/audio/VorbisDecoder");
    gNativeInstanceFieldID=env->GetFieldID(clazz,"m_nativeInstance","I");
}

extern "C" void
Java_com_noisetracks_android_audio_VorbisDecoder_nativeClose(JNIEnv * env,jobject thiz) {
	VorbisDecoder * decoder=getInstance(env,thiz);
	if (decoder) {
		decoder->Close();
		delete decoder;
		setInstance(env,thiz,0);
	}
}

extern "C" jint
Java_com_noisetracks_android_audio_VorbisDecoder_nativeOpen(JNIEnv * env,jobject thiz,jstring pathString) {
	Java_com_noisetracks_android_audio_VorbisDecoder_nativeClose(env,thiz);
	VorbisDecoder * decoder=new VorbisDecoder();
	int openError;
	{
		const char * path=env->GetStringUTFChars(pathString,0);
		openError=decoder->Open(path);
		env->ReleaseStringUTFChars(pathString,path);
	}
	if (openError!=0) {
		delete decoder;
		return openError;
	}
	setInstance(env,thiz,decoder);
	return 0;
}

extern "C" jboolean
Java_com_noisetracks_android_audio_VorbisDecoder_nativeIsSeekable(JNIEnv * env,jobject thiz) {
	VorbisDecoder * decoder=getInstance(env,thiz);
	if (!decoder) {
		return JNI_FALSE;
	}
	return decoder->IsSeekable()?JNI_TRUE:JNI_FALSE;
}

extern "C" jint
Java_com_noisetracks_android_audio_VorbisDecoder_nativeGetRate(JNIEnv * env,jobject thiz) {
	VorbisDecoder * decoder=getInstance(env,thiz);
	if (!decoder) {
		return 0;
	}
	return decoder->GetRate();
}

extern "C" jint
Java_com_noisetracks_android_audio_VorbisDecoder_nativeGetChannels(JNIEnv * env,jobject thiz) {
	VorbisDecoder * decoder=getInstance(env,thiz);
	if (!decoder) {
		return 0;
	}
	return decoder->GetChannels();
}

extern "C" jint
Java_com_noisetracks_android_audio_VorbisDecoder_nativeGetTimeLength(JNIEnv * env,jobject thiz) {
	VorbisDecoder * decoder=getInstance(env,thiz);
	if (!decoder) {
		return 0;
	}
	return decoder->GetTimeLength();
}

extern "C" jint
Java_com_noisetracks_android_audio_VorbisDecoder_nativeGetTimePosition(JNIEnv * env,jobject thiz) {
	VorbisDecoder * decoder=getInstance(env,thiz);
	if (!decoder) {
		return 0;
	}
	return decoder->GetTimePosition();
}

extern "C" jint
Java_com_noisetracks_android_audio_VorbisDecoder_nativeSeekToTime(JNIEnv * env,jobject thiz,jint time) {
	VorbisDecoder * decoder=getInstance(env,thiz);
	if (!decoder) {
		return OV_FALSE;
	}
	return decoder->SeekToTime(time);
}

extern "C" jint
Java_com_noisetracks_android_audio_VorbisDecoder_nativeRead(JNIEnv * env,jobject thiz,jbyteArray bufferArray,jint offset,jint length) {
	VorbisDecoder * decoder=getInstance(env,thiz);
	if (!decoder) {
		return 0;
	}
	jbyte * buffer=env->GetByteArrayElements(bufferArray,0);
	int totalRead=0;
	while (length>0) {
		int read=decoder->Read(buffer+offset,length);
		if (read<=0) {
			if (!totalRead && read<0) {
				totalRead=read;
			}
			break;
		}
		totalRead+=read;
		offset+=read;
		length-=read;
	}
	env->ReleaseByteArrayElements(bufferArray,buffer,0);
	return totalRead;
}
