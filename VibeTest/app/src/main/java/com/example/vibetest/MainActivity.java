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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStreamWriter;

import ca.uol.aig.fftpack.RealDoubleFFT;


public class MainActivity extends AppCompatActivity implements SensorEventListener {
    int channelConfiguration = AudioFormat.CHANNEL_CONFIGURATION_MONO;
    int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
    private RealDoubleFFT transformer;
    int blockSize = 256;                         // deal with this many samples at a time
    boolean started = false;
    public int frequency = 21000;                      // the frequency given
    Button bNormalVibration, bClickVibration, bDoubleClickVibration, bTickVibration, startStopButton;
    boolean ultraSound = false;
    RecordAudio recordTask;

    ImageView imageView;
    Bitmap bitmap;
    Canvas canvas;
    Paint paint;

    public class RecordAudio extends AsyncTask<Void, double[], Void> {
        public void run() {

            Toast.makeText(getApplicationContext(), "Example for Toast", Toast.LENGTH_SHORT).show();

        }
        @Override
        protected Void doInBackground(Void... arg0) {

            try {
                // int bufferSize = AudioRecord.getMinBufferSize(frequency,
                // AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                int bufferSize = AudioRecord.getMinBufferSize(frequency,
                        channelConfiguration, audioEncoding);

                if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return null;
                }
                AudioRecord audioRecord = new AudioRecord(
                        MediaRecorder.AudioSource.MIC, frequency,
                        channelConfiguration, audioEncoding, bufferSize);

                short[] buffer = new short[blockSize];
                double[] toTransform = new double[blockSize];

                audioRecord.startRecording();

                // started = true; hopes this should true before calling
                // following while loop
                while (started) {
                    int bufferReadResult = audioRecord.read(buffer, 0,
                            blockSize);

                    for (int i = 0; i < blockSize && i < bufferReadResult; i++) {
                        toTransform[i] = (double) buffer[i] / 32767.0; // signed
                        // 16
                    }                                       // bit
                    transformer.ft(toTransform);
                    /*
                    for( int j = 0; j < toTransform.length; j++){
                        if(toTransform[j] > 2100){
                            Log.e("AudioRecord", "Recording 20khz");
                            ultraSound = true;
                            started = false;

                            audioRecord.stop();
                        }
                    }
                    */

                    publishProgress(toTransform);



                }

                audioRecord.stop();
                run();

            } catch (Throwable t) {
                t.printStackTrace();
                Log.e("AudioRecord", "Recording Failed");
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(double[]... toTransform) {

            canvas.drawColor(Color.BLACK);

            for (int i = 0; i < toTransform[0].length; i++) {
                int x = i;
                int downy = (int) (100 - (toTransform[0][i] * 10));
                int upy = 100;

                canvas.drawLine(x, downy, x, upy, paint);
            }

            imageView.invalidate();

            // TODO Auto-generated method stub
            // super.onProgressUpdate(values);
        }

    }

    private SensorManager sensorManager;
    private Sensor accel;
    //private Sensor gyro;
    private Long startTime;
    private Long curTime;
    float[] accelerometer_data = new float[3];
    float[] gravity = new float[3];
    String filename = "vibecheck.csv";
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
                try (OutputStreamWriter output = new OutputStreamWriter(openFileOutput(filename, Context.MODE_APPEND))) {
                    output.write(String.valueOf(curTime));
                    output.write(",");

                    /*
                    output.write(String.valueOf(accelerometer_data[0]));


                    output.write(",");
                    output.write(String.valueOf(accelerometer_data[1]));
                    output.write(",");
                    */

                    output.write(String.valueOf(accelerometer_data[2]));
                    output.write("\n");

                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
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

        imageView = (ImageView) this.findViewById(R.id.ImageView01);
        bitmap = Bitmap.createBitmap((int) 256, (int) 100,
                Bitmap.Config.ARGB_8888);
        canvas = new Canvas(bitmap);
        paint = new Paint();
        paint.setColor(Color.GREEN);
        imageView.setImageBitmap(bitmap);



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
                final VibrationEffect vibrationEffect4;

                // this type of vibration requires API 29
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {

                    // create vibrator effect with the constant EFFECT_TICK
                    vibrationEffect4 = VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK);

                    // it is safe to cancel other vibrations currently taking place
                    vibrator.cancel();

                    vibrator.vibrate(vibrationEffect4);
                }
            }
        });


        // handle heavy click vibration button
        startStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (started) {
                    started = false;
                    startStopButton.setText("Start");
                    recordTask.cancel(true);
                } else {
                    started = true;
                    Log.e("AudioRecord", "Recording");
                    startStopButton.setText("Stop");
                    recordTask = new RecordAudio();
                    recordTask.execute();
                }
            }
        });
    }





    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}

