package com.example.testaudioanalysis;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import android.os.Process;
import android.util.Pair;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;

import java.sql.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.concurrent.LinkedBlockingDeque;

public class FrequencyAnalyzer {
    boolean mShouldContinue;
    final int SAMPLE_RATE = 44100;
    final int MIN_AMPLITUDE = 50; // The threshold a frequency must meet to be measured

    Thread mAudioPoll;

    int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);

    private int bufferSize = minBufferSize;

    void setFrequencyResolution(float resolution){
        float newBufferSize = 2 * SAMPLE_RATE / resolution;
        bufferSize = nextPowerOfTwo((int) Math.ceil(newBufferSize));
    }


    void createPollAudioThread(final TextView v, final ImageView i){
        mShouldContinue = true;
        mAudioPoll = new Thread(new Runnable() {
            @Override
            public void run() {
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);

                if(bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE){
                    bufferSize = SAMPLE_RATE * 2;
                }


                short[] audioBuffer;

                //Bump up to power of 2 for FFT
                //if(!isPowerOfTwo(bufferSize)){
                //    bufferSize = nextPowerOfTwo(bufferSize);
                //    bufferSize = nextPowerOfTwo(bufferSize);
                //}

                //Set audio buffer to hold shorts (2 bytes each)
                audioBuffer = new short[bufferSize / 2];
                // Set how many audio samples taken at once
                final int SAMPLE_LENGTH = audioBuffer.length / 4;//Sample length depends on resolution

                AudioRecord record = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);

                if(record.getState() != AudioRecord.STATE_INITIALIZED){
                    return;
                }
                NoteIdentifier noteIdentifier = new NoteIdentifier();
                FastFourierTransformer fft = new FastFourierTransformer(DftNormalization.STANDARD);
                record.startRecording();

                float fromDegree= 0.0f;
                float toDegree = 0.0f;
                while(mShouldContinue){

                    // Shift array indices by sample length
                    System.arraycopy(audioBuffer,0,audioBuffer,SAMPLE_LENGTH,SAMPLE_LENGTH * 3);
                    record.read(audioBuffer,0,SAMPLE_LENGTH);
                    //short[] new_array = Arrays.copyOfRange(audioBuffer,0,SAMPLE_LENGTH);
                    double[] d = shortToDouble(audioBuffer);
                    d = HanningWindow(d);
                    Complex[] c = fft.transform(d, TransformType.FORWARD);

                    //Get the frequency with the highest amplitude. Convert "buckets" to frequencies
                    double df = (double) getDominantFrequencyBucketRETURNMAX(c) * SAMPLE_RATE / audioBuffer.length;

                    //double df = (double) getDominantFrequencyBucketMULTIPLESTRATEGY(c) * SAMPLE_RATE / audioBuffer.length;

                    final Pair<String, Double> p = noteIdentifier.identify(df);

                    if(p != null) {
                        final float off = p.second.floatValue();
                        if(off >= 0.5 && off <= 1.0)
                            toDegree = 180 * off - 180;
                        else
                            toDegree = 180 * off;
                        final float td = toDegree;
                        final float fd = fromDegree;
                        //Update ui with the frequency
                        Utils.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                final RotateAnimation rotateAnimation = new RotateAnimation(fd,td,
                                        Animation.RELATIVE_TO_SELF,0.5f,
                                        Animation.RELATIVE_TO_SELF,1.23f);
                                rotateAnimation.setDuration(250);
                                rotateAnimation.setFillAfter(true);
                                i.startAnimation(rotateAnimation);
                                v.setText(p.first);
                                if(off < 0.1 || off > 0.9)
                                    v.setTextColor(Color.GREEN);
                                else
                                    v.setTextColor(Color.RED);
                            }
                        });
                    }

                    fromDegree = toDegree;

                }
                record.stop();
                record.release();
            }
        });
    }

    public void runPollAudio(){
        mAudioPoll.start();
    }

    public void stopPollAudio(){
        mShouldContinue = false;
        mAudioPoll = null;
    }

    //Finds the fundamental overtone of the signal
    private int getDominantFrequencyBucketRETURNMAX(Complex[] c){//, int sample_rate, int buffer_length){
        int dominantFrequency = 0;
        double dominantAmplitude = 0;
        double target = (double) MIN_AMPLITUDE * SAMPLE_RATE / 2;

        for(int i = 0; i < c.length / 2 + 1; i++) {
            if(c[i].abs() > dominantAmplitude){
                dominantAmplitude = c[i].abs();
                dominantFrequency = i;
            }
        }

        if(dominantAmplitude < target) return 0;

        return dominantFrequency;
    }

    private int getDominantFrequencyBucketMULTIPLESTRATEGY(Complex[] c){
        int dominantFrequency = 0;
        double dominantAmplitude = 0;
        double target = (double) MIN_AMPLITUDE * SAMPLE_RATE / 2;

        for(int i = 0; i < c.length / 2 + 1; i++){
            if(c[i].abs() > dominantAmplitude && Math.log(i) - Math.log(dominantFrequency) < 0.33){
                i = dominantFrequency;
                dominantAmplitude = c[i].abs();
            }
        }

        return dominantFrequency;
    }

    private double[] shortToDouble(short[] s){
        double[] d = new double[s.length];

        for(int i = 0; i < d.length; i++){
            d[i] = (double) s[i];
        }
        return d;
    }

    private double[] HanningWindow(double[] d){
        int N = d.length;

        for(int i = 0; i < N; i++){
            d[i] = Math.pow(Math.sin(Math.PI * i / N),2) * d[i];
        }
        return d;
    }

    private boolean isPowerOfTwo(int n){
        return (int)(Math.ceil((Math.log(n) / Math.log(2)))) ==
                (int)(Math.floor(((Math.log(n) / Math.log(2)))));
    }

    private int nextPowerOfTwo(int n){
        double d = Math.ceil((Math.log(n)/Math.log(2)));

        if(Math.round(d) == d){
            d++;
        }

        double r = Math.pow(2,d);
        return (int) r;
    }
}
