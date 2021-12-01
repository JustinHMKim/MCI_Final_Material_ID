package com.example.vibetest;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.io.android.AudioDispatcherFactory;
import be.tarsos.dsp.pitch.PitchDetectionHandler;
import be.tarsos.dsp.pitch.PitchDetectionResult;
import be.tarsos.dsp.pitch.PitchProcessor;
import ca.uol.aig.fftpack.RealDoubleFFT;


public class MainActivity extends AppCompatActivity implements SensorEventListener {
    int channelConfiguration = AudioFormat.CHANNEL_CONFIGURATION_MONO;
    int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
    private RealDoubleFFT transformer;
    int blockSize = 4096;                         // deal with this many samples at a time
    boolean startRec = false;
    boolean startPlay = false;
    public int frequency = 21000;                      // the frequency given
    Button bNormalVibration, bClickVibration, bDoubleClickVibration, bTickVibration, startStopButton, startStopButton2;
    boolean ultraSound = false;

    String filename;
    static final int AUDIO_SOURCE = MediaRecorder.AudioSource.MIC; // for raw audio, use MediaRecorder.AudioSource.UNPROCESSED, see note in MediaRecorder section
    static final int SAMPLE_RATE = 44100;
    final static int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_8BIT;
    static final int BUFFER_SIZE_RECORDING = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);

    private String audioFilename = null;
    private MediaRecorder recorder = null;
    private boolean start = false;

    private void onRecord(boolean start) {
        if (start) {
            startRecording();
        } else {
            stopRecording();
        }
    }


    private void startRecording() {
        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(AudioFormat.ENCODING_PCM_16BIT);
        recorder.setOutputFile(audioFilename);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        recorder.setAudioEncodingBitRate(160000);
        recorder.setAudioSamplingRate(44100);
        try {
            recorder.prepare();
        } catch (IOException e) {
            Log.e("AudioRecordTest", "prepare() failed");
        }

        recorder.start();

    }

    private void stopRecording() {
        recorder.stop();
        recorder.release();
        recorder = null;
    }

    private SensorManager sensorManager;
    private Sensor accel;
    //private Sensor gyro;
    private Long startTime;
    private Long curTime;
    float[] accelerometer_data = new float[3];
    float[] gravity = new float[3];
    String csv_name = "vibecheck.csv";
    TextView tv;

    // buttons for all the types of the vibration effects

    private final int duration = 5; // seconds
    //    private final int sampleRate = 21000;
    private final int sampleRate = 192000;
    private final int numSamples = duration * sampleRate;
    private final double[] sample = new double[numSamples];

    private final byte[] generatedSnd = new byte[2 * numSamples];
    private String [] permissions = {Manifest.permission.RECORD_AUDIO};

    // construct AudioRecord to record audio from microphone with sample rate of 44100Hz

    void connectsAudioDispatchertoMicrophone() {
        //final String[] hrtz = {""};
        AudioDispatcher dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(44100, 4096, 0);

        PitchDetectionHandler pdh = new PitchDetectionHandler() {
            @Override
            public void handlePitch(final PitchDetectionResult result, AudioEvent e) {
                final float pitchInHz = result.getPitch();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (pitchInHz > 1000)
                            Log.d("TAG", "pitchInHz: " + pitchInHz);
                            //hrtz[0] = Float.toString(pitchInHz);
                    }
                });
            }
        };
        AudioProcessor p = new PitchProcessor(PitchProcessor.PitchEstimationAlgorithm.FFT_YIN,
                44100,
                4096,
                pdh);
        dispatcher.addAudioProcessor(p);

        Thread thread = new Thread(dispatcher, "Audio Dispatcher");
        thread.start();
    }


    void genTone() {
        // fill out the array
        for (int i = 0; i < numSamples; ++i) {
            // hz
            double freqOfTone = 21000;
            sample[i] = Math.sin(2 * Math.PI * i / (sampleRate / freqOfTone));
        }

        // convert to 16 bit pcm sound array
        // assumes the sample buffer is normalised.
        int idx = 0;
        for (final double dVal : sample) {
            // scale to maximum amplitude
            final short val = (short) ((dVal * 32767));
            // in 16 bit wav PCM, first byte is the low order byte
            generatedSnd[idx++] = (byte) (val & 0x00ff);
            generatedSnd[idx++] = (byte) ((val & 0xff00) >>> 8);

        }
    }

    void playSound() {
        final AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                sampleRate, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT, generatedSnd.length,
                AudioTrack.MODE_STATIC);
        audioTrack.write(generatedSnd, 0, generatedSnd.length);
        audioTrack.play();
    }

    Handler handler = new Handler();

    @Override
    public void onSensorChanged(SensorEvent event) {

        if(startTime == null){
            startTime = event.timestamp;
        }
        curTime = event.timestamp - startTime;
        switch (event.sensor.getType()) {

            case Sensor.TYPE_ACCELEROMETER:
                final float alpha = 0.8f;

                // Isolate the force of gravity with the low-pass filter.
                //gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0];
                //gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1];
                gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2];

                //accelerometer_data[0] = event.values[0] - gravity[0];
                //accelerometer_data[1] = event.values[1] - gravity[1];
                accelerometer_data[2] = event.values[2] - gravity[2];
                /*try (OutputStreamWriter output = new OutputStreamWriter(openFileOutput(filename, Context.MODE_APPEND))) {
                    output.write(String.valueOf(curTime));
                    output.write(",");


                    //output.write(String.valueOf(accelerometer_data[0]));


                    //output.write(",");
                    //output.write(String.valueOf(accelerometer_data[1]));
                    //output.write(",");


                    output.write(String.valueOf(accelerometer_data[2]));
                    output.write("\n");

                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                */
                break;

            default:
                return;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        transformer = new RealDoubleFFT(blockSize);

        ActivityCompat.requestPermissions(this, permissions, 200);
        audioFilename = "data/data/com.example.vibetest/files/myrecording.wav";

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        //gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_FASTEST);
        //sensorManager.registerListener(this, gyro, SensorManager.SENSOR_DELAY_NORMAL);

        // get the VIBRATOR_SERVICE system service
        final Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        // register all of the buttons with their IDs
        bNormalVibration = findViewById(R.id.normalVibrationButton);
        bClickVibration = findViewById(R.id.clickVibrationButton);
        bDoubleClickVibration = findViewById(R.id.doubleClickVibrationButton);
        bTickVibration = findViewById(R.id.tickVibrationButton);
        startStopButton = findViewById(R.id.startStopButton);
        startStopButton2 = findViewById(R.id.startStopButton2);

        // Makes sound
        bNormalVibration.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                // this is the only type of the vibration which requires system version Oreo (API 26)

                final Thread soundThread = new Thread(() -> {
                    genTone();
                    handler.post(() -> playSound());
                });

                final Thread vibeThread = new Thread(() -> {
                    final VibrationEffect vibrationEffect1;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {

                        // this effect creates the vibration of default amplitude for 1000ms(1 sec)
                        vibrationEffect1 = VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE);

                        // it is safe to cancel other vibrations currently taking place
                        vibrator.cancel();
                        vibrator.vibrate(vibrationEffect1);
                    }
                    else {
                        ((Vibrator) getSystemService(VIBRATOR_SERVICE)).vibrate(1500);
                    }
                });

                soundThread.start();
                //vibeThread.start();

            }
        });

        // Records audio
        bClickVibration.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                connectsAudioDispatchertoMicrophone();

            }
        });

        // handle double click vibration button
        bDoubleClickVibration.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                final VibrationEffect vibrationEffect3;

                // this type of vibration requires API 29
                File file = new File("data/data/com.example.vibetest/files/myrecording.wav");
                try {
                    file.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                filename = file.toString();
            }
        });

        // handle tick effect vibration button
        bTickVibration.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if(startRec){
                    Toast t = Toast.makeText(getApplicationContext(), "Stop Rec", Toast.LENGTH_SHORT);
                    t.show();
                    startRec = false;
                }
                else{
                    Toast t = Toast.makeText(getApplicationContext(), "Stop Play", Toast.LENGTH_SHORT);
                    t.show();
                    startPlay = false;
                }
            }
        });


        // handle heavy click vibration button
        startStopButton.setOnClickListener(new View.OnClickListener() {
            @Override

            public void onClick(View v) {
                startRec = true;
                /*
                if (startPlay) {
                    startStopButton.setText("Playing File");
                    final Thread playThread = new Thread(() -> {
                        startPlaying();
                        readAudioData(filename);
                    });
                    playThread.start();

                } else {
                    startStopButton.setText("Can Start Playing File");
                    startPlay = true;
                }
                */

            }
        });
        startStopButton2.setOnClickListener(new View.OnClickListener() {
            @Override

            public void onClick(View v) {


                if (startRec) {
                    Log.e("AudioRecord", "Recording");
                    startStopButton2.setText("Record");
                    final Thread recThread = new Thread(() -> {
                        onRecord(true);
                    });
                    startRec = false;
                    recThread.start();

                } else {
                    startStopButton2.setText("Can Start Record");
                    onRecord(false);
                    startRec = true;
                }
            }
        });
    }





    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}

