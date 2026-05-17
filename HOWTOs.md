# HOWTOs

## 1. Howto build lib-decoder-ffmpeg-release.aar on linux

#### Download source code

- Android NDK:

https://github.com/android/ndk/wiki/Unsupported-Downloads

API19: r25c  https://dl.google.com/android/repository/android-ndk-r25c-linux.zip

API17: r23c  https://dl.google.com/android/repository/android-ndk-r23c-linux.zip

- FFMPEG

https://www.ffmpeg.org/download.html

https://ffmpeg.org/releases/ffmpeg-7.0.1.tar.xz

- Androidx media

API19: https://github.com/androidx/media/tree/1.3.0

API17: https://github.com/androidx/media/tree/1.2.1

#### Build

0. Unzip all code to ~/ffmpeg

1. Setup env

- API19
```
export ANDROID_ABI=19
export MEDIA_RROJECT=~/ffmpeg/media-1.3.0
export NDK_PATH=~/ffmpeg/android-ndk-r25c

export FFMPEG_MODULE_PATH=$MEDIA_RROJECT/libraries/decoder_ffmpeg/src/main
export FFMPEG_PATH=~/ffmpeg/ffmpeg-7.0.1
export HOST_PLATFORM="linux-x86_64"
export ENABLED_DECODERS=(vorbis opus flac alac mp3 aac ac3 eac3)
```
- API17:
```
export ANDROID_ABI=17
export NDK_PATH=~/ffmpeg/android-ndk-r23c
export MEDIA_RROJECT=~/ffmpeg/media-1.2.1

export FFMPEG_MODULE_PATH=$MEDIA_RROJECT/libraries/decoder_ffmpeg/src/main
...
```

2. Link ffmpeg source code

```
ln -s $FFMPEG_PATH $FFMPEG_MODULE_PATH/jni/ffmpeg
```

3. Build ffmpeg

Modify  $FFMPEG_MODULE_PATH/jni/build_ffmpeg.sh if you need.
```
cd $FFMPEG_MODULE_PATH/jni
./build_ffmpeg.sh "${FFMPEG_MODULE_PATH}" "${NDK_PATH}" "${HOST_PLATFORM}" "${ANDROID_ABI}" "${ENABLED_DECODERS[@]}"
```

4. Build aar

modify common_library_config.gradle if you need.
```
    defaultConfig {
	    ......
	    minSdkVersion 19  #API17: 17
        ndk {
            abiFilters 'armeabi-v7a', 'arm64-v8a'
        }
    }
```
```
cd $MEDIA_RROJECT
./gradlew lib-decoder-ffmpeg:assembleRelease
```

5. Copy file to project

```
cd $MEDIA_RROJECT/libraries/decoder_ffmpeg/buildout/outputs/aar
cp lib-decoder-ffmpeg-release.aar <project-path-to/my-tv-0/app/libs/>
```

