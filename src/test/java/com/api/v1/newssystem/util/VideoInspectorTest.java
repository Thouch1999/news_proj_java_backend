package com.api.v1.newssystem.util;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class VideoInspectorTest {

    @Test
    void anMp4WithH264_isRecognised_withItsDuration() {
        VideoInspector.Info info = VideoInspector.inspect(VideoFixtures.mp4("avc1", 12.5)).orElseThrow();

        assertThat(info.type()).isEqualTo("mp4");
        assertThat(info.videoCodec()).isEqualTo("avc1");
        assertThat(info.seconds()).isEqualTo(12.5);
        assertThat(info.isNotH264()).isFalse();
    }

    @Test
    void anMp4WithAnotherVideoCodec_isKnownNotToBeH264() {
        for (String codec : new String[]{"hvc1", "hev1", "av01", "vp09"}) {
            VideoInspector.Info info = VideoInspector.inspect(VideoFixtures.mp4(codec, 5.0)).orElseThrow();
            assertThat(info.isNotH264()).as(codec).isTrue();
        }
    }

    @Test
    void theBrandMustBeOfTheMp4Family() {
        assertThat(VideoInspector.inspect(VideoFixtures.mp4("mp42", "avc1", 5.0))).isPresent();
        assertThat(VideoInspector.inspect(VideoFixtures.mp4("qt  ", "avc1", 5.0))).isEmpty(); // QuickTime
        assertThat(VideoInspector.inspect(VideoFixtures.mp4("heic", "avc1", 5.0))).isEmpty(); // HEIF
    }

    @Test
    void aDurationThatCannotBeRead_isUnknown_notAnError() {
        assertThat(VideoInspector.inspect(VideoFixtures.mp4("avc1", null)).orElseThrow().seconds()).isNull();
        assertThat(VideoInspector.inspect(VideoFixtures.webm("webm", null)).orElseThrow().seconds()).isNull();
    }

    @Test
    void aWebM_isRecognised_withItsDuration() {
        VideoInspector.Info info = VideoInspector.inspect(VideoFixtures.webm("webm", 29.0)).orElseThrow();

        assertThat(info.type()).isEqualTo("webm");
        assertThat(info.seconds()).isEqualTo(29.0);
        assertThat(info.isNotH264()).isFalse();
    }

    @Test
    void aMatroskaFile_isNotAWebM() {
        assertThat(VideoInspector.inspect(VideoFixtures.webm("matroska", 10.0))).isEmpty();
    }

    @Test
    void anythingElse_isNotAVideo() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};
        byte[] exe = {'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0, 4, 0, 0, 0};
        byte[] avi = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'A', 'V', 'I', ' '};
        for (byte[] data : new byte[][]{png, exe, avi, new byte[0], new byte[5]}) {
            assertThat(VideoInspector.inspect(data)).isEmpty();
        }
    }

    @Test
    void aFileThatIsCutOrBroken_neverThrows() {
        byte[] whole = VideoFixtures.mp4("avc1", 10.0);
        for (int length : new int[]{12, 20, 40, 60, whole.length - 1}) {
            byte[] cut = java.util.Arrays.copyOf(whole, length);
            Optional<VideoInspector.Info> info = VideoInspector.inspect(cut);
            assertThat(info).as("cut at " + length).isPresent(); // the container is still an MP4: only the size limit is left
        }
        byte[] webm = VideoFixtures.webm("webm", 10.0);
        for (int length : new int[]{4, 8, 20, webm.length - 1}) {
            assertThat(VideoInspector.inspect(java.util.Arrays.copyOf(webm, length))).as("webm cut at " + length).isNotNull();
        }
    }
}
