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
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.io.android.AudioDispatcherFactory;
import be.tarsos.dsp.pitch.PitchDetectionHandler;
import be.tarsos.dsp.pitch.PitchDetectionResult;
import be.tarsos.dsp.pitch.PitchProcessor;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

  int channelConfiguration = AudioFormat.CHANNEL_CONFIGURATION_MONO;
  int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
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

  private String audioFilename = "data/data/com.example.vibetest/files/myrecording.wav";
  private MediaRecorder recorder = null;
  // ---------------------------------------

  private SensorManager sensorManager;
  private Sensor accel;
  float[] accelerometer_data = new float[3];
  float[] gravity = new float[3];
  String filename = "transmitter_data.csv";
  String receiverFilename = "receiver_data.csv";

  // buttons for all the types of the vibration effects
  Button bNormalVibration, bTickVibration, bHeavyClickVibration, bStopRanging;
  Button bReceiverStartRanging, bReceiverStopRanging;
  Button bPlaySound, bStartRecording;
  Button bStartSoundRanging, bStopSoundRanging;
  Button bReceiverStartSoundRanging, bReceiverStopSoundRanging;

  TextView transmitterData;


  /**
   * variables for transmitter sound ranging
   */
  private boolean startSoundRanging = false;
  private Long st4 = null;
  private Long st5 = null;
//  private Long rangingStartTime = null;
//  private Long currTime = null;

//  private boolean signalReceived = false;
//  private Boolean prevSignalReceived = null;
//  private boolean firstTime = true;
//  private boolean transmitterStopRecording = false;

  /**
   * variables for transmitter vibration ranging
   */
  private boolean startRanging = false;
  // t1
  private Long rangingStartTime = null;
  private Long vt4 = null;
  private Long vt5 = null;
  private Long currTime = null;

  private boolean signalReceived = false;
  private Boolean prevSignalReceived = null;
  private boolean transmitterStopRecording = false;
  private int vTransIter = 0;
  private int sTransIter = 0;
  /**
   * variables for receiver sound ranging
   */
  private boolean receiverStartSoundRanging = false;
  private boolean receiverStopSoundRecording = false;
//  private Long receiverRangingStartTime = null;
//  private Long receiverStopRecording = null;
//
//  private boolean receiverSignalReceived = false;
//  private Boolean prevReceiverSignalReceived = null;
//  private boolean receiverFirstTime = true;
//  private boolean receiverSecondTime = true;
  private Long st2 = null;
  private Long st3 = null;
  private Long st6 = null;
  /**
   * variables for receiver vibration ranging
   */
  private boolean receiverStartRanging = false;
  private Long receiverRangingStartTime = null;
  private Long vt2 = null;
  private Long vt3 = null;
  private Long vt6 = null;
  boolean finished = false;
  private Long receiverStopRecording = null;

  private boolean receiverSignalReceived = false;
  private Boolean prevReceiverSignalReceived = null;
  private int vReceiverIter = 0;
  private int sReceiverIter = 0;
  /**
   * variables for sound generation and recording
   */
  private final int duration = 2; // seconds
  private final int sampleRate = 192000;
  private final int numSamples = duration * sampleRate;
  private final double[] sample = new double[numSamples];

  private final byte[] generatedSnd = new byte[2 * numSamples];
  private String [] permissions = {Manifest.permission.RECORD_AUDIO};


  /**
   * Helper Functions for sound recording and generation
   */
  private void onRecord(boolean start) {
    if (start) {
      startRecording();
    } else {
      stopRecording();
    }
  }

  // Records Audio
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


  void genTone() {
    // fill out the array
    for (int i = 0; i < numSamples; ++i) {
      // hz
      double freqOfTone = 14000;
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
    transmitterStopRecording = true;
    receiverStopSoundRecording = true;
    final AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT, generatedSnd.length, AudioTrack.MODE_STATIC);
    audioTrack.write(generatedSnd, 0, generatedSnd.length);
    audioTrack.play();
    try {
      Thread.sleep(3000);
    } catch (Exception e) {
      e.printStackTrace();
    }
    transmitterStopRecording = false;
    receiverStopSoundRecording = false;
  }



  // construct AudioRecord to record audio from microphone with sample rate of 44100Hz
  // Detects Audio frequency, does ranging
  void connectsAudioDispatchertoMicrophone() {
    AudioDispatcher dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(44100, 4096, 0);

    PitchDetectionHandler pdh = new PitchDetectionHandler() {
      @Override
      public void handlePitch(final PitchDetectionResult result, AudioEvent e) {
        final float pitchInHz = result.getPitch();
        if (startSoundRanging && !transmitterStopRecording) {
            if (pitchInHz >= 14000) {
              signalReceived = true;
              prevSignalReceived = true;
              if (sTransIter == 0) {
                sTransIter++;
                st4 = System.nanoTime();
                //final Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

                final Thread soundThread = new Thread(() -> {
                  genTone();
                  handler.post(() -> playSound());
                });
                try {
                  Thread.sleep(5000);
                } catch (InterruptedException interruptedException) {
                  interruptedException.printStackTrace();
                }
                transmitterStopRecording = true;
                st5 = System.nanoTime(); // t5 = t4 + 5s
                soundThread.start();
              }
            }
        }

//        Log.d("recording", String.valueOf(receiverStopSoundRecording));
        if (receiverStartSoundRanging && !receiverStopSoundRecording) {
            if (pitchInHz >= 14000) {
              if (sReceiverIter == 0) {
                sReceiverIter++;
                st2 = System.nanoTime();

                final Thread soundThread = new Thread(() -> {
                  genTone();
                  handler.post(() -> playSound());
                });
                receiverStopSoundRecording = true;
                try {
                  Thread.sleep(5000);
                } catch (InterruptedException interruptedException) {
                  interruptedException.printStackTrace();
                }
                Log.d("FIRST", String.valueOf(System.nanoTime()));
                st3 = System.nanoTime(); // t3 = t2 + 5 * 10^9
                soundThread.start();
              } else if (sReceiverIter == 1 && !receiverStopSoundRecording) {
                sReceiverIter++;
                Log.d("SECOND", String.valueOf(System.nanoTime()));
                st6 = System.nanoTime();
              }
            }

        }

        runOnUiThread(new Runnable() {
          @Override
          public void run() {
//            if (pitchInHz > 1000 && !transmitterStopRecording)
//              Log.d("TAG", "pitchInHz: " + pitchInHz);
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


  Handler handler = new Handler();


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
          if (Math.abs(accelerometer_data[2]) > 0.13 ) {
            receiverSignalReceived = true;
            prevReceiverSignalReceived = true;
            Log.d("vReceiverIter", vReceiverIter + "");
            if (vReceiverIter == 0) {
              vReceiverIter++;
              Log.d("vReceiverIter", vReceiverIter + "when it should be 0");
              vt2 = System.nanoTime();
              final Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
              new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                  final Thread vibeThread = new Thread(() -> {

                    vt3 = System.nanoTime(); // t3 = t2 + 5 * 10^9
                    Log.d("vt3", vt3 +" ");
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

                  receiverStopRecording = System.nanoTime();
                  vibeThread.start();
                  finished = true;
                }
              }, 5000);
            } else if (finished) {
              vReceiverIter++;
              vt6 = System.nanoTime();
              Log.d("vt6", vt6 +" ");
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
          if (Math.abs(accelerometer_data[2]) > 0.13 && Math.abs(accelerometer_data[2]) < 0.135) {
            Log.d("Argh", accelerometer_data[2] + " ");
            signalReceived = true;
            prevSignalReceived = true;
            if (vTransIter == 0 ) {
              vTransIter++;

              vt4 = System.nanoTime();
              Log.d("T4", vt4 + " ");
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
                  vt5 = System.nanoTime(); // t5 = t4 + 5s
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

    ActivityCompat.requestPermissions(this, permissions, 200);

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
    bStopRanging = findViewById(R.id.stopRangingButton);
    bReceiverStartRanging = findViewById(R.id.receiverStartRangingButton);
    bReceiverStopRanging = findViewById(R.id.receiverStopRangingButton);
    bPlaySound = findViewById(R.id.playSoundButton);
    bStartRecording = findViewById(R.id.startRecordButton);
    bStartSoundRanging = findViewById(R.id.startSoundRangingButton);
    bStopSoundRanging = findViewById(R.id.stopSoundRangingButton);
    bReceiverStartSoundRanging = findViewById(R.id.receiverStartSoundRangingButton);
    bReceiverStopSoundRanging = findViewById(R.id.receiverStopSoundRangingButton);

    transmitterData = findViewById(R.id.transmitterData);

    // Receiver Start Ranging
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

    // Receiver Stop Ranging
    bReceiverStopRanging.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        if (!receiverStartRanging) {
          Toast invalidClick = Toast.makeText(getApplicationContext(), "Vibration ranging hasn't started.", Toast.LENGTH_SHORT);
          invalidClick.show();
          return;
        }


        double Db = ((double)vt3 - vt2) / 1000000000;
        double Rb = ((double)vt6 - vt3) / 1000000000;
        StringBuilder sb = new StringBuilder();
        sb.append("Db: " + Db + "\n");
        sb.append("Rb: " + Rb);
        transmitterData.setText(sb.toString());

        receiverStartRanging = false;
        receiverRangingStartTime = null;
        vt2 = null;
        vt3 = null;
        vt6 = null;
        receiverStopRecording = null;
        receiverSignalReceived = false;
        vReceiverIter = 0;
      }
    });

    // Transmitter Start Ranging
    bNormalVibration.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        rangingStartTime = System.nanoTime();
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
            vibrationEffect1 = VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE);
            // it is safe to cancel other vibrations currently taking place
            vibrator.cancel();
            vibrator.vibrate(vibrationEffect1);
          } else {
            ((Vibrator) getSystemService(VIBRATOR_SERVICE)).vibrate(500);
          }
        });


        Log.d("T1", rangingStartTime + " ");
        vibeThread.start();

      }
    });

    // Transmitter Stop Ranging
    bStopRanging.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        if (!startRanging) {
          Toast invalidClick = Toast.makeText(getApplicationContext(), "Vibration ranging hasn't started.", Toast.LENGTH_SHORT);
          invalidClick.show();
          return;
        }

        double Ra = ((double)vt4 - rangingStartTime) / 1000000000;
        double Da = ((double)vt5 - vt4) / 1000000000;
        StringBuilder sb = new StringBuilder();
        sb.append("Ra: " + Ra + "\n");
        sb.append("Da: " + Da);
        transmitterData.setText(sb.toString());

        startRanging = false;
        // t1
        rangingStartTime = null;
        vt4 = null;
        vt5 = null;
        currTime = null;
        signalReceived = false;
        prevReceiverSignalReceived = null;
        vTransIter = 0;
        transmitterStopRecording = false;
      }
    });

    bReceiverStartSoundRanging.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        if (startSoundRanging || receiverStartSoundRanging) {
          String text = startRanging ? "You are the transmitter" : "Ranging has already started";
          Toast invalidClick = Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT);
          invalidClick.show();
          return;
        }

        File f = new File(audioFilename);
        try {
          f.createNewFile();
        } catch (IOException e) {
          e.printStackTrace();
        }

        receiverStartSoundRanging = true;
        receiverStopSoundRecording = false;

        connectsAudioDispatchertoMicrophone();

        receiverRangingStartTime = System.nanoTime();
      }
    });

    bReceiverStopSoundRanging.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        if (!receiverStartSoundRanging) {
          Toast invalidClick = Toast.makeText(getApplicationContext(), "Sound ranging hasn't started.", Toast.LENGTH_SHORT);
          invalidClick.show();
          return;
        }

        double Db = ((double) st3 - st2) / 1000000000;
        double Rb = ((double) st6 - st3) / 1000000000;
        StringBuilder sb = new StringBuilder();
        sb.append("Db: " + Db + "\n");
        sb.append("Rb: " + Rb);
        transmitterData.setText(sb.toString());


        receiverStopSoundRecording = false;
        receiverStartSoundRanging = false;
        receiverRangingStartTime = null;
        st2 = null;
        st3 = null;
        st6 = null;
        receiverStopRecording = null;
        receiverSignalReceived = false;
        sReceiverIter = 0;
      }
    });

    bStartSoundRanging.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        if (startSoundRanging) {
          Toast invalidClick = Toast.makeText(getApplicationContext(), "Sound Ranging has already started!",
                  Toast.LENGTH_SHORT);
          invalidClick.show();
          return;
        }

        File f = new File(audioFilename);
        try {
          f.createNewFile();
        } catch (IOException e) {
          e.printStackTrace();
        }
        startSoundRanging = true;
//        startRecording = true;

        transmitterStopRecording = true;
        connectsAudioDispatchertoMicrophone();

        // makes sound
        final Thread soundThread = new Thread(() -> {
          genTone();
          handler.post(() -> playSound());
        });

        rangingStartTime = System.nanoTime();
//        Log.d("Start time:", String.valueOf(rangingStartTime));
        soundThread.start();
      }
    });

    bStopSoundRanging.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        if (!startSoundRanging) {
          Toast invalidClick = Toast.makeText(getApplicationContext(), "Sound ranging hasn't started.", Toast.LENGTH_SHORT);
          invalidClick.show();
          return;
        }

        double Ra = ((double) st4 - rangingStartTime) / 1000000000;
        double Da = ((double) st5 - st4) / 1000000000;
        StringBuilder sb = new StringBuilder();
        sb.append("Ra: " + Ra + "\n");
        sb.append("Da: " + Da);
        transmitterData.setText(sb.toString());

        startSoundRanging = false;
        rangingStartTime = null;
        st4 = null;
        st5 = null;
        currTime = null;
        signalReceived = false;
        prevReceiverSignalReceived = null;
        sTransIter = 0;
        transmitterStopRecording = false;
      }
    });

    // makes sound
    bPlaySound.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        // this is the only type of the vibration which requires system version Oreo (API 26)
        transmitterStopRecording = true;
        final Thread soundThread = new Thread(() -> {
          genTone();
          handler.post(() -> playSound());
        });
        soundThread.start();
        transmitterStopRecording = false;
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

  }

  @Override
  public void onAccuracyChanged(Sensor sensor, int accuracy) {

  }
}
