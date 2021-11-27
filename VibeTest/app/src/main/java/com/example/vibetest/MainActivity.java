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

import ca.uol.aig.fftpack.RealDoubleFFT;


public class MainActivity extends AppCompatActivity implements SensorEventListener {
    int channelConfiguration = AudioFormat.CHANNEL_CONFIGURATION_MONO;
    int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
    private RealDoubleFFT transformer;
    int blockSize = 4096;                         // deal with this many samples at a time
    boolean started = false;
    public int frequency = 21000;                      // the frequency given
    Button bNormalVibration, bClickVibration, bDoubleClickVibration, bTickVibration, startStopButton;
    boolean ultraSound = false;

    String filename;
    static final int AUDIO_SOURCE = MediaRecorder.AudioSource.MIC; // for raw audio, use MediaRecorder.AudioSource.UNPROCESSED, see note in MediaRecorder section
    static final int SAMPLE_RATE = 44100;
    final static int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_8BIT;
    static final int BUFFER_SIZE_RECORDING = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
    protected AudioRecord audioRecord;

    private void startRecording() {

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        audioRecord = new AudioRecord(AUDIO_SOURCE, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, BUFFER_SIZE_RECORDING);

        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) { // check for proper initialization
            Log.e("TAG", "error initializing ");
            return;
        }

        audioRecord.startRecording();

    }

    private void writeAudioData(String fileName) { // to be called in a Runnable for a Thread created after call to startRecording()
        byte[] data = new byte[BUFFER_SIZE_RECORDING/2]; // assign size so that bytes are read in in chunks inferior to AudioRecord internal buffer size
        FileOutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream(fileName); //fileName is path to a file, where audio data should be written
        } catch (FileNotFoundException e) {
// handle error
        }

        if(!started){
            Log.d("TAG", "kill me " );
        }
        else{
            Log.d("TAG", "please for the love of God " );
        }
        while (started) { // continueRecording can be toggled by a button press, handled by the main (UI) thread
            int read = audioRecord.read(data, 0, data.length);
            try {
                outputStream.write(data, 0, read);
            }
            catch (IOException e) {
                Log.d("TAG", "exception while writing to file");
                e.printStackTrace();
            }
        }
        try {
            outputStream.flush();
            outputStream.close();
        }
        catch (IOException e) {
            Log.d("TAG", "exception while closing output stream " + e.toString());
            e.printStackTrace();
        }
// Clean up
        audioRecord.stop();
        audioRecord.release();
        audioRecord = null;
    }


    final static int CHANNEL_OUT_CONFIG = AudioFormat.CHANNEL_OUT_MONO;

    static final int BUFFER_SIZE_PLAYING = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT_CONFIG, AUDIO_FORMAT);

    protected AudioTrack audioTrack;

    private void startPlaying() {

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH) // defines the type of content being played
                .setUsage(AudioAttributes.USAGE_MEDIA) // defines the purpose of why audio is being played in the app
                .build();

        AudioFormat audioFormat = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_8BIT) // we plan on reading byte arrays of data, so use the corresponding encoding
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build();

        audioTrack = new AudioTrack(audioAttributes, audioFormat, BUFFER_SIZE_PLAYING, AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE);

    }

    private void readAudioData(String fileName) { // fileName is the path to the file where the audio data is located
        byte[] data = new byte[BUFFER_SIZE_PLAYING/2]; // small buffer size to not overflow AudioTrack's internal buffer
        FileInputStream fileInputStream = null;
        try {
            fileInputStream = new FileInputStream(new File(fileName));
        }
        catch (IOException e) {
// handle exception
        }
        int i = 0;
        while (i != -1) { // run until file ends
            try {
                i = fileInputStream.read(data);
                audioTrack.write(data, 0, i);
            }
            catch (IOException e) {
// handle exception
            }
        }
        try {
            fileInputStream.close();
        }
        catch (IOException e) {
// handle exception
        }
        audioTrack.stop();
        audioTrack.release();
        audioTrack = null;
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

    // construct AudioRecord to record audio from microphone with sample rate of 44100Hz


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

        // handle normal vibration button
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

        // handle click vibration button
        bClickVibration.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                StringBuilder sb = new StringBuilder();

                sb.append("vibecheck.csv");

                File to = new File(getFilesDir(), sb.toString());

            }
        });

        // handle double click vibration button
        bDoubleClickVibration.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                final VibrationEffect vibrationEffect3;

                // this type of vibration requires API 29
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {

                    // create vibrator effect with the constant EFFECT_DOUBLE_CLICK
                    vibrationEffect3 = VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK);

                    // it is safe to cancel other vibrations currently taking place
                    vibrator.cancel();

                    vibrator.vibrate(vibrationEffect3);
                }
            }
        });

        // handle tick effect vibration button
        bTickVibration.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                File file = new File("data/data/com.example.vibetest/files/myrecording.mp3");
                filename = file.toString();
                if(!started){
                    Toast t = Toast.makeText(getApplicationContext(), "Click the bottom button to play the file", Toast.LENGTH_SHORT);
                    t.show();
                    started = true;
                }
                else{
                    Toast t = Toast.makeText(getApplicationContext(), "Click the bottom button record to file", Toast.LENGTH_SHORT);
                    t.show();
                    started = false;
                }
            }
        });


        // handle heavy click vibration button
        startStopButton.setOnClickListener(new View.OnClickListener() {
            @Override

            public void onClick(View v) {


                final Thread playThread = new Thread(() -> {
                    startRecording();
                    writeAudioData(filename);
                });
                final Thread recThread = new Thread(() -> {
                    startPlaying();
                    readAudioData(filename);
                });
                if (started) {
                    startStopButton.setText("Play File");
                    playThread.start();

                } else {
                    Log.e("AudioRecord", "Recording");
                    startStopButton.setText("Record");
                    recThread.start();
                }
            }
        });
    }





    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}

