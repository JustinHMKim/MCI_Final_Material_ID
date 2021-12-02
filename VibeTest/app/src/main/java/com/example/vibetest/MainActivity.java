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
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.List;

import androidx.core.app.ActivityCompat;
import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.AudioAttributes;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.util.Log;
import java.io.FileInputStream;
import java.io.FileOutputStream;

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
  boolean ultraSound = false;

  static final int AUDIO_SOURCE = MediaRecorder.AudioSource.MIC; // for raw audio, use MediaRecorder.AudioSource.UNPROCESSED, see note in MediaRecorder section
  static final int SAMPLE_RATE = 44100;
  final static int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
  static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_8BIT;
  static final int BUFFER_SIZE_RECORDING = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);

  private String audioFilename = null;
  private MediaRecorder recorder = null;
  private boolean start = false;
  // ---------------------------------------

  private SensorManager sensorManager;
  private Sensor accel;
  // private Sensor gyro;
  // private Long startTime;
  // private Long curTime;
  float[] accelerometer_data = new float[3];
  float[] gravity = new float[3];
  String filename = "transmitter_data.csv";
  String receiverFilename = "receiver_data.csv";

  // buttons for all the types of the vibration effects
  Button bNormalVibration, bTickVibration, bHeavyClickVibration, bStopRanging;
  Button bReceiverStartRanging, bReceiverStopRanging;
  Button bPlaySound, bStartRecording;

  TextView transmitterData;

  private boolean startRanging = false;
  private Long rangingStartTime = null;
  private Long t4 = null;
  private Long t5 = null;
  private Long currTime = null;

  private boolean signalReceived = false;
  private Boolean prevSignalReceived = null;
  private boolean firstTime = true;
  private boolean transmitterStopRecording = false;


  private final int duration = 5; // seconds
  // private final int sampleRate = 21000;
  private final int sampleRate = 192000;
  private final int numSamples = duration * sampleRate;
  private final double[] sample = new double[numSamples];

  private final byte[] generatedSnd = new byte[2 * numSamples];
  private String [] permissions = {Manifest.permission.RECORD_AUDIO};


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
      double freqOfTone = 15000;
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
    final AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT, generatedSnd.length, AudioTrack.MODE_STATIC);
    audioTrack.write(generatedSnd, 0, generatedSnd.length);
    audioTrack.play();
  }

  Handler handler = new Handler();

  private boolean receiverStartRanging = false;
  private Long receiverRangingStartTime = null;
  private Long t2 = null;
  private Long t3 = null;
  private Long t6 = null;

  private Long receiverStopRecording = null;

  private boolean receiverSignalReceived = false;
  private Boolean prevReceiverSignalReceived = null;
  private boolean receiverFirstTime = true;
  private boolean receiverSecondTime = true;

  @Override
  public void onSensorChanged(SensorEvent event) {

    if ((!startRanging || rangingStartTime == null) && (!receiverStartRanging || receiverRangingStartTime == null))
      return;

    currTime = event.timestamp;
    if (receiverStartRanging) {
      switch (event.sensor.getType()) {
      case Sensor.TYPE_ACCELEROMETER:
        // Isolate the force of gravity with the low-pass filter.
        final float alpha = 0.8f;
        gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2];
        accelerometer_data[2] = event.values[2] - gravity[2];

        if (receiverStopRecording == null || System.nanoTime() - receiverStopRecording < 5 * 1000000000) {
          if (Math.abs(accelerometer_data[2]) > 0.13) {
            receiverSignalReceived = true;
            prevReceiverSignalReceived = true;
            if (receiverFirstTime) {
              receiverFirstTime = false;
              t2 = System.nanoTime();
              final Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
              new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                  final Thread vibeThread = new Thread(() -> {
                    final VibrationEffect vibrationEffect1;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                      // this effect creates the vibration of default amplitude for 1000ms(1 sec)
                      vibrationEffect1 = VibrationEffect.createOneShot(1500, VibrationEffect.DEFAULT_AMPLITUDE);
                      // it is safe to cancel other vibrations currently taking place
                      vibrator.cancel();
                      vibrator.vibrate(vibrationEffect1);
                    } else {
                      ((Vibrator) getSystemService(VIBRATOR_SERVICE)).vibrate(1500);
                    }
                  });
                  t3 = System.nanoTime(); // t3 = t2 + 5 * 10^9
                  receiverStopRecording = System.nanoTime();
                  vibeThread.start();
                }
              }, 5000);
            } else if (receiverSecondTime) {
              receiverSecondTime = false;
              t6 = System.nanoTime();
            }
          } else {
            if (prevReceiverSignalReceived == null || !prevReceiverSignalReceived) {
              receiverSignalReceived = false;
              prevReceiverSignalReceived = false;
            } else {
              receiverSignalReceived = true;
              prevReceiverSignalReceived = false;
            }
          }
        }

        try (OutputStreamWriter output = new OutputStreamWriter(openFileOutput(receiverFilename, Context.MODE_APPEND))) {
          output.write(String.valueOf(receiverRangingStartTime));
          output.write(",");
          output.write(String.valueOf(currTime));
          output.write(",");
          output.write(String.valueOf(accelerometer_data[2]));
          output.write(",");
          output.write(String.valueOf(receiverSignalReceived));
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
    } else {
      switch (event.sensor.getType()) {
      case Sensor.TYPE_ACCELEROMETER:
        final float alpha = 0.8f;
        // Isolate the force of gravity with the low-pass filter.
        // gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0];
        // gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1];
        gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2];

        // accelerometer_data[0] = event.values[0] - gravity[0];
        // accelerometer_data[1] = event.values[1] - gravity[1];
        accelerometer_data[2] = event.values[2] - gravity[2];

        if (System.nanoTime() - rangingStartTime > 5 * 1000000000 && !transmitterStopRecording) {
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
                      vibrationEffect1 = VibrationEffect.createOneShot(1500, VibrationEffect.DEFAULT_AMPLITUDE);
                      // it is safe to cancel other vibrations currently taking place
                      vibrator.cancel();
                      vibrator.vibrate(vibrationEffect1);
                    } else {
                      ((Vibrator) getSystemService(VIBRATOR_SERVICE)).vibrate(1500);
                    }
                  });
                  transmitterStopRecording = true;
                  t5 = System.nanoTime(); // t5 = t4 + 5s
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
           * output.write(String.valueOf(accelerometer_data[0])); output.write(",");
           * output.write(String.valueOf(accelerometer_data[1])); output.write(",");
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
    // gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
    sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_FASTEST);
    // sensorManager.registerListener(this, gyro,
    // SensorManager.SENSOR_DELAY_NORMAL);

    // get the VIBRATOR_SERVICE system service
    final Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

    // register all of the buttons with their IDs
    bNormalVibration = findViewById(R.id.normalVibrationButton);
    bTickVibration = findViewById(R.id.tickVibrationButton);
    bHeavyClickVibration = findViewById(R.id.heavyClickVibrationButton);
    bStopRanging = findViewById(R.id.stopRangingButton);
    bReceiverStartRanging = findViewById(R.id.receiverStartRangingButton);
    bReceiverStopRanging = findViewById(R.id.receiverStopRangingButton);
    bPlaySound = findViewById(R.id.playSoundButton);
    bStartRecording = findViewById(R.id.startRecordButton);

    transmitterData = findViewById(R.id.transmitterData);

    bReceiverStartRanging.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        if (startRanging || receiverStartRanging) {
          String text = startRanging ? "You are the transmitter" : "Ranging has already started";
          Toast invalidClick = Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT);
          invalidClick.show();
          return;
        }

        File f = new File(getFilesDir(), receiverFilename);
        f.delete();
        receiverStartRanging = true;
        receiverRangingStartTime = System.nanoTime();
      }
    });

    bReceiverStopRanging.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        if (!receiverStartRanging) {
          Toast invalidClick = Toast.makeText(getApplicationContext(), "Ranging hasn't started.", Toast.LENGTH_SHORT);
          invalidClick.show();
          return;
        }


        double Db = ((double)t3 - t2) / 1000000000;
        double Rb = ((double)t6 - t3) / 1000000000;
        StringBuilder sb = new StringBuilder();
        sb.append("Db: " + Db + "\n");
        sb.append("Rb: " + Rb);
        transmitterData.setText(sb.toString());

        receiverStartRanging = false;
        receiverRangingStartTime = null;
        t2 = null;
        t3 = null;
        t6 = null;
        receiverStopRecording = null;
        receiverSignalReceived = false;
        receiverFirstTime = true;
        receiverSecondTime = true;
      }
    });

    // makes sound
    bPlaySound.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        // this is the only type of the vibration which requires system version Oreo (API 26)
        final Thread soundThread = new Thread(() -> {
          genTone();
          handler.post(() -> playSound());
        });
        soundThread.start();
      }
    });

    // records audio
    // + creates file
    // + startRec set to true
    bStartRecording.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        File file = new File("data/data/com.example.vibetest/files/myrecording.wav");
        try {
          file.createNewFile();
        } catch (IOException e) {
          e.printStackTrace();
        }
        filename = file.toString();
        
        startRec = true;
        connectsAudioDispatchertoMicrophone();
      }
    });

    // handle normal vibration button
    bNormalVibration.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        if (startRanging) {
          Toast invalidClick = Toast.makeText(getApplicationContext(), "Ranging has already started!",
              Toast.LENGTH_SHORT);
          invalidClick.show();
          return;
        }

        File f = new File(getFilesDir(), filename);
        f.delete();
        startRanging = true;

        final Thread vibeThread = new Thread(() -> {
          final VibrationEffect vibrationEffect1;
          if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            // this effect creates the vibration of default amplitude for 1000ms(1 sec)
            vibrationEffect1 = VibrationEffect.createOneShot(1500, VibrationEffect.DEFAULT_AMPLITUDE);
            // it is safe to cancel other vibrations currently taking place
            vibrator.cancel();
            vibrator.vibrate(vibrationEffect1);
          } else {
            ((Vibrator) getSystemService(VIBRATOR_SERVICE)).vibrate(1500);
          }
        });

        rangingStartTime = System.nanoTime();
        // soundThread.start();
        vibeThread.start();
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

        double Ra = ((double)t4 - rangingStartTime) / 1000000000;
        double Da = ((double)t5 - t4) / 1000000000;
        StringBuilder sb = new StringBuilder();
        sb.append("Ra: " + Ra + "\n");
        sb.append("Da: " + Da);
        transmitterData.setText(sb.toString());

        startRanging = false;
        // t1
        rangingStartTime = null;
        t4 = null;
        t5 = null;
        currTime = null;
        signalReceived = false;
        prevReceiverSignalReceived = null;
        firstTime = true;
        transmitterStopRecording = false;
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
