package com.example.vibetest;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity implements SensorEventListener {


    private SensorManager sensorManager;
    private Sensor accel;
    //private Sensor gyro;
//    private Long startTime;
//    private Long curTime;
    float[] accelerometer_data = new float[3];
    float[] gravity = new float[3];
    String filename = "vibecheck.csv";

    // buttons for all the types of the vibration effects
    Button bNormalVibration, bClickVibration, bDoubleClickVibration, bTickVibration, bHeavyClickVibration, bStopRanging;

    private boolean startRanging = false;
    private Long rangingStartTime = null;
    private Long t4 = null;
    private Long currTime = null;

    private boolean signalReceived = false;
    private Boolean prevSignalReceived = null;
    private boolean firstTime = true;

    private final int duration = 5; // seconds
    //    private final int sampleRate = 21000;
    private final int sampleRate = 192000;
    private final int numSamples = duration * sampleRate;
    private final double[] sample = new double[numSamples];

    private final byte[] generatedSnd = new byte[2 * numSamples];

    void genTone() {
        // fill out the array
        for (int i = 0; i < numSamples; ++i) {
            // hz
            double freqOfTone = 2000;
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
        if (!startRanging || rangingStartTime == null)
            return;

        currTime = event.timestamp;
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

                if (System.nanoTime() - rangingStartTime > 5 * 1000000000) {
                    if (Math.abs(accelerometer_data[2]) > 0.13) {
                        signalReceived = true;
                        prevSignalReceived = true;
                        if (firstTime) {
                            firstTime = false;
                            t4 = System.nanoTime();
                            final Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                            new Handler().postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    final Thread vibeThread = new Thread(() -> {
                                        final VibrationEffect vibrationEffect1;
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                            // this effect creates the vibration of default amplitude for 1000ms(1 sec)
                                            vibrationEffect1 = VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE);
                                            // it is safe to cancel other vibrations currently taking place
                                            vibrator.cancel();
                                            vibrator.vibrate(vibrationEffect1);
                                        }
                                        else {
                                            ((Vibrator) getSystemService(VIBRATOR_SERVICE)).vibrate(1500);
                                        }
                                    });


                                    vibeThread.start();
                                }
                            }, 5000);
                        }
                    } else {
                        if (prevSignalReceived == null || !prevSignalReceived) {
                            signalReceived = false;
                            prevSignalReceived = false;
                        } else {
                            signalReceived = true;
                            prevSignalReceived = false;
                        }
                    }
                }



                try (OutputStreamWriter output = new OutputStreamWriter(openFileOutput(filename, Context.MODE_APPEND))) {
                    output.write(String.valueOf(rangingStartTime));
                    output.write(",");
                    output.write(String.valueOf(currTime));
                    output.write(",");

                    /*
                    output.write(String.valueOf(accelerometer_data[0]));
                    output.write(",");
                    output.write(String.valueOf(accelerometer_data[1]));
                    output.write(",");
                    */

                    output.write(String.valueOf(accelerometer_data[2]));
                    output.write(",");
                    output.write(String.valueOf(signalReceived));
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
        bHeavyClickVibration = findViewById(R.id.heavyClickVibrationButton);
        bStopRanging = findViewById(R.id.stopRangingButton);

        // handle normal vibration button
        bNormalVibration.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (startRanging) {
                    Toast invalidClick = Toast.makeText(getApplicationContext(), "Ranging has started!", Toast.LENGTH_SHORT);
                    invalidClick.show();
                    return;
                }

                File f = new File(getFilesDir(), filename);
                f.delete();
                startRanging = true;
                firstTime = true;

                // this is the only type of the vibration which requires system version Oreo (API 26)
//                final Thread soundThread = new Thread(() -> {
//                    genTone();
//                    handler.post(() -> playSound());
//                });

                final Thread vibeThread = new Thread(() -> {
                    final VibrationEffect vibrationEffect1;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        // this effect creates the vibration of default amplitude for 1000ms(1 sec)
                        vibrationEffect1 = VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE);
                        // it is safe to cancel other vibrations currently taking place
                        vibrator.cancel();
                        vibrator.vibrate(vibrationEffect1);
                    }
                    else {
                        ((Vibrator) getSystemService(VIBRATOR_SERVICE)).vibrate(1500);
                    }
                });

                rangingStartTime = System.nanoTime();
//                soundThread.start();
                vibeThread.start();

//                new Handler().postDelayed(new Runnable() {
//                    @Override
//                    public void run() {
//                        final Thread vibeThread = new Thread(() -> {
//                            final VibrationEffect vibrationEffect1;
//                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
//                                // this effect creates the vibration of default amplitude for 1000ms(1 sec)
//                                vibrationEffect1 = VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE);
//                                // it is safe to cancel other vibrations currently taking place
//                                vibrator.cancel();
//                                vibrator.vibrate(vibrationEffect1);
//                            }
//                            else {
//                                ((Vibrator) getSystemService(VIBRATOR_SERVICE)).vibrate(1500);
//                            }
//                        });
//                        vibeThread.start();
//                    }
//                }, 5000);

            }
        });

        bStopRanging.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!startRanging) {
                    Toast invalidClick = Toast.makeText(getApplicationContext(), "Ranging hasn't started.", Toast.LENGTH_SHORT);
                    invalidClick.show();
                    return;
                }

                startRanging = false;
                rangingStartTime = null;
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
        bHeavyClickVibration.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final VibrationEffect vibrationEffect5;

                // this type of vibration requires API 29
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {

                    // create vibrator effect with the constant EFFECT_HEAVY_CLICK
                    vibrationEffect5 = VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK);

                    // it is safe to cancel other vibrations currently taking place
                    vibrator.cancel();

                    vibrator.vibrate(vibrationEffect5);
                }
            }
        });
    }





    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}

