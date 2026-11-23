package com.ecoquest.core;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.Obuffer;
import javazoom.jl.decoder.SampleBuffer;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

/** Small MP3 playback wrapper so supplied tracks follow the in-game audio controls. */
final class Mp3Playback {
    private final File file;
    private final boolean loop;
    private final Object pauseLock = new Object();
    private volatile boolean running = true;
    private volatile boolean paused;
    private volatile int volume = 100;
    private volatile SourceDataLine line;
    private final Thread worker;

    Mp3Playback(File file, boolean loop) {
        this.file = file;
        this.loop = loop;
        worker = new Thread(this::play, "ecoquest-audio-" + file.getName());
        worker.setDaemon(true);
    }

    void start() { worker.start(); }
    boolean isFinished() { return !running; }

    void setVolume(int value) { volume = Math.max(0, Math.min(100, value)); }

    void setPaused(boolean value) {
        synchronized (pauseLock) {
            paused = value;
            SourceDataLine current = line;
            if (current != null) {
                if (value) current.stop();
                else current.start();
            }
            pauseLock.notifyAll();
        }
    }

    void stop() {
        running = false;
        synchronized (pauseLock) { pauseLock.notifyAll(); }
        SourceDataLine current = line;
        if (current != null) {
            current.stop();
            current.flush();
            current.close();
        }
    }

    private void play() {
        try {
            do {
                if (!decodePass()) break;
            } while (running && loop);
        } catch (Exception e) {
            System.err.println("Could not play " + file.getName() + ": " + e.getMessage());
        } finally {
            SourceDataLine current = line;
            if (current != null) {
                try {
                    if (running) current.drain();
                } catch (IllegalStateException ignored) {
                    // The game may close the line while stopping or changing tracks.
                } finally {
                    current.stop();
                    current.close();
                }
            }
            running = false;
        }
    }

    private boolean decodePass() throws Exception {
        if (!file.isFile()) return false;
        try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
            Bitstream bitstream = new Bitstream(input);
            Decoder decoder = new Decoder();
            boolean decodedAny = false;
            try {
                Header header;
                while (running && (header = bitstream.readFrame()) != null) {
                    Obuffer decoded = decoder.decodeFrame(header, bitstream);
                    bitstream.closeFrame();
                    if (!(decoded instanceof SampleBuffer samples)) continue;
                    ensureLine(samples);
                    short[] pcm = samples.getBuffer();
                    int sampleCount = samples.getBufferLength();
                    byte[] bytes = new byte[sampleCount * 2];
                    int gain = volume;
                    for (int i = 0; i < sampleCount; i++) {
                        int sample = gain == 0 ? 0 : (pcm[i] * gain) / 100;
                        bytes[i * 2] = (byte) sample;
                        bytes[i * 2 + 1] = (byte) (sample >> 8);
                    }
                    decodedAny = true;
                    int offset = 0;
                    while (running && offset < bytes.length) {
                        awaitUnpaused();
                        if (!running) break;
                        int count = Math.min(4096, bytes.length - offset);
                        int written = line.write(bytes, offset, count);
                        offset += written;
                    }
                }
            } finally {
                bitstream.close();
            }
            return decodedAny;
        }
    }

    private void ensureLine(SampleBuffer samples) throws Exception {
        if (line != null && line.isOpen()) return;
        AudioFormat format = new AudioFormat(samples.getSampleFrequency(), 16,
                samples.getChannelCount(), true, false);
        SourceDataLine created = AudioSystem.getSourceDataLine(format);
        created.open(format, 8192);
        line = created;
        if (!paused && running) created.start();
    }

    private void awaitUnpaused() {
        synchronized (pauseLock) {
            while (running && paused) {
                try { pauseLock.wait(100); }
                catch (InterruptedException ignored) { Thread.currentThread().interrupt(); return; }
            }
            if (running && !paused && line != null) line.start();
        }
    }
}
