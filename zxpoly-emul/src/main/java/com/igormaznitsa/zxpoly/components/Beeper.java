package com.igormaznitsa.zxpoly.components;

import static com.igormaznitsa.zxpoly.components.VideoController.CYCLES_BETWEEN_INT;
import static java.lang.String.format;
import static java.util.Arrays.fill;
import static javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED;
import static javax.sound.sampled.AudioFormat.Encoding.PCM_UNSIGNED;


import com.igormaznitsa.jbbp.utils.JBBPUtils;
import com.igormaznitsa.zxpoly.MainForm;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Exchanger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

public class Beeper {

  private static final Logger LOGGER = Logger.getLogger("Beeper");

  private static final byte SND_LEVEL0 = 0; // 00
  private static final byte SND_LEVEL1 = 50; // 01
  private static final byte SND_LEVEL2 = (byte) 200; // 10
  private static final byte SND_LEVEL3 = (byte) 255; // 11

  private static final boolean LOG_RAW_SOUND = false;

  private static final IBeeper NULL_BEEPER = new IBeeper() {
    @Override
    public void updateState(boolean intSignal, long machineCycleInInt, int portFed4d3) {
    }

    @Override
    public float getMasterGain() {
      return -1.0f;
    }

    @Override
    public void setMasterGain(float valueInDb) {

    }

    @Override
    public void dispose() {
    }

    @Override
    public void reset() {
    }

    @Override
    public void start() {

    }

    @Override
    public void pause() {

    }

    @Override
    public void resume() {

    }
  };

  private final AtomicReference<IBeeper> activeInternalBeeper = new AtomicReference<>(NULL_BEEPER);

  public Beeper() {
  }

  public void reset() {
    this.activeInternalBeeper.get().reset();
  }

  public void setEnable(final boolean flag) {
    if (flag && this.activeInternalBeeper.get() == NULL_BEEPER) {
      try {
        final IBeeper newInternalBeeper = new InternalBeeper();
        if (this.activeInternalBeeper.compareAndSet(NULL_BEEPER, newInternalBeeper)) {
          newInternalBeeper.start();
        }
      } catch (LineUnavailableException | IllegalArgumentException ex) {
        LOGGER.severe("Can't create beeper: " + ex.getMessage());
      }
    } else {
      this.activeInternalBeeper.getAndSet(NULL_BEEPER).dispose();
    }
  }

  public void resume() {
    this.activeInternalBeeper.get().resume();
  }

  public void pause() {
    this.activeInternalBeeper.get().pause();
  }

  public void updateState(boolean intSignal, long machineCycleInInt, int portFeD4D3) {
    this.activeInternalBeeper.get().updateState(intSignal, machineCycleInInt, portFeD4D3);
  }

  public boolean isActive() {
    return this.activeInternalBeeper.get() != NULL_BEEPER;
  }

  public void dispose() {
    this.activeInternalBeeper.get().dispose();
  }

  private interface IBeeper {
    void start();

    float getMasterGain();

    void setMasterGain(float valueInDb);

    void pause();

    void resume();

    void updateState(boolean intSignal, long machineCycleInInt, int portFeD4D3);

    void dispose();

    void reset();
  }

  private static final class InternalBeeper implements IBeeper, Runnable {
    private static final int SND_FREQ = 44100;
    private static final int NUM_OF_BUFFERS = 3;
    private static final AudioFormat AUDIO_FORMAT = new AudioFormat(
        PCM_UNSIGNED,
        SND_FREQ,
        8,
        1,
        1,
        SND_FREQ,
        false
    );
    private static final int SAMPLES_IN_INT = SND_FREQ / 50;
    private final byte[][] soundBuffers = new byte[NUM_OF_BUFFERS][SAMPLES_IN_INT];
    private final Exchanger<byte[]> exchanger = new Exchanger<>();
    private final SourceDataLine sourceDataLine;
    private final Thread thread;
    private int activeBufferIndex = 0;
    private int lastPosition = 0;
    private final AtomicBoolean paused = new AtomicBoolean();
    private volatile boolean working = true;
    private final AtomicReference<FloatControl> gainControl = new AtomicReference<>();

    @Override
    public void start() {
      if (this.working) {
        this.thread.start();
      }
    }

    @Override
    public void pause() {
      if (this.working) {
        if (this.paused.compareAndSet(false, true)) {
          LOGGER.info("Paused");
        }
      }
    }

    @Override
    public void resume() {
      if (this.working) {
        if (this.paused.compareAndSet(true, false)) {
          LOGGER.info("Resumed");
        }
      }
    }

    private byte lastValue = SND_LEVEL0;

    private void initMasterGain() {
      final FloatControl gainControl = this.gainControl.get();
      if (gainControl != null) {
        gainControl.setValue(-40.0f);
      }
    }

    @Override
    public float getMasterGain() {
      final FloatControl gainControl = this.gainControl.get();
      if (gainControl == null || !this.working) {
        return -1.0f;
      } else {
        return gainControl.getValue();
      }
    }

    @Override
    public void setMasterGain(final float valueInDb) {
      final FloatControl gainControl = this.gainControl.get();
      if (gainControl != null && this.working) {
        gainControl.setValue(Math.max(gainControl.getMinimum(), Math.min(gainControl.getMaximum(), valueInDb)));
      }
    }

    private InternalBeeper() throws LineUnavailableException {
      for (byte[] b : this.soundBuffers) {
        fill(b, SND_LEVEL0);
      }
      this.sourceDataLine = (SourceDataLine) AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, AUDIO_FORMAT));
      final Line.Info lineInfo = this.sourceDataLine.getLineInfo();
      LOGGER.info("Got sound data line: " + lineInfo.toString());

      this.thread = new Thread(this, "beeper-thread-" + System.nanoTime());
      this.thread.setDaemon(true);
      this.thread.setPriority(Thread.MAX_PRIORITY);
    }

    private void blink() {
      if (this.working) {
        final byte[] currentBuffer = this.soundBuffers[this.activeBufferIndex++];
        this.lastPosition = 0;
        if (this.activeBufferIndex >= NUM_OF_BUFFERS) {
          this.activeBufferIndex = 0;
        }
        try {
          exchanger.exchange(currentBuffer, 20, TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
          // DO NOTHING, JUST LOST DATA
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
        }
      }
    }

    @Override
    public void updateState(
        final boolean intSignal,
        long machineCycleInInt,
        final int portFeD4D3
    ) {
      if (this.working) {
        final byte value;
        switch (portFeD4D3) {
          case 0:
            value = SND_LEVEL0;
            break;
          case 1:
            value = SND_LEVEL1;
            break;
          case 2:
            value = SND_LEVEL2;
            break;
          default:
            value = SND_LEVEL3;
            break;
        }

        if (machineCycleInInt > CYCLES_BETWEEN_INT) {
          fill(this.soundBuffers[this.activeBufferIndex], this.lastPosition, SAMPLES_IN_INT, this.lastValue);
          blink();
          machineCycleInInt -= CYCLES_BETWEEN_INT;
        }

        int position = (int) (machineCycleInInt * SAMPLES_IN_INT / CYCLES_BETWEEN_INT);

        if (position < this.lastPosition) {
          fill(this.soundBuffers[this.activeBufferIndex], this.lastPosition, SAMPLES_IN_INT, this.lastValue);
          blink();
        }

        fill(this.soundBuffers[this.activeBufferIndex], this.lastPosition, position, this.lastValue);
        if (position == SAMPLES_IN_INT) {
          blink();
          position = 0;
        }
        this.soundBuffers[this.activeBufferIndex][position] = value;

        this.lastValue = value;
        this.lastPosition = position;
      }
    }

    @Override
    public void reset() {
      if (this.working) {
        LOGGER.info("Reseting");
        lastPosition = 0;
        lastValue = SND_LEVEL0;
        for (byte[] b : this.soundBuffers) {
          fill(b, SND_LEVEL0);
        }
      }
    }

    private String makeFileNameForFormat(final AudioFormat format) {
      final DateFormat dataFormat = new SimpleDateFormat("hhmmss");

      return format("bpr_hz%d_ch%d_%s_%s_%s.raw",
          (int) format.getSampleRate(),
          format.getChannels(),
          format.getEncoding() == PCM_SIGNED ? "SGN" : "USGN",
          format.isBigEndian() ? "bge" : "lte",
          dataFormat.format(new Date()));
    }

    private OutputStream makeLogStream(final File folder) {
      if (!LOG_RAW_SOUND) {
        return null;
      }
      try {
        final File logFile = new File(folder, makeFileNameForFormat(AUDIO_FORMAT));
        LOGGER.info("Log beeper raw data into " + logFile);
        return new BufferedOutputStream(new FileOutputStream(logFile));
      } catch (Exception ex) {
        LOGGER.warning("Can't create log file:" + ex.getMessage());
        return null;
      }
    }

    private void writeWholeArray(final byte[] data) {
      int pos = 0;
      int len = data.length;
      while (len > 0 && this.working && !Thread.currentThread().isInterrupted()) {
        final int written = this.sourceDataLine.write(data, pos, len);
        pos += written;
        len -= written;
      }
    }

    @Override
    public void run() {
      LOGGER.info("Starting thread");
      final byte[] localBuffer = new byte[SAMPLES_IN_INT];
      fill(localBuffer, SND_LEVEL0);

      final OutputStream logStream = makeLogStream(new File("./"));
      try {
        this.sourceDataLine.open(AUDIO_FORMAT, SAMPLES_IN_INT << 2);
        if (this.sourceDataLine.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
          final FloatControl gainControl = (FloatControl) this.sourceDataLine.getControl(FloatControl.Type.MASTER_GAIN);
          LOGGER.info(format("Got master gain control %f..%f", gainControl.getMinimum(), gainControl.getMaximum()));
          this.gainControl.set(gainControl);
        } else {
          LOGGER.warning("Master gain control is not supported");
        }
        this.initMasterGain();

        LOGGER.info(format("Sound line opened, buffer size is %d byte(s)", this.sourceDataLine.getBufferSize()));
        writeWholeArray(new byte[this.sourceDataLine.getBufferSize()]);
        this.sourceDataLine.start();
        writeWholeArray(localBuffer);

        LOGGER.info("Sound line started");

        while (this.working && !Thread.currentThread().isInterrupted()) {
          try {
            final byte[] buffer = exchanger.exchange(null, MainForm.TIMER_INT_DELAY_MILLISECONDS + 10, TimeUnit.MILLISECONDS);
            if (buffer == null) {
              fill(localBuffer, SND_LEVEL0);
            } else {
              System.arraycopy(buffer, 0, localBuffer, 0, SAMPLES_IN_INT);
              if (LOG_RAW_SOUND && logStream != null) {
                logStream.write(localBuffer);
              }
            }
            writeWholeArray(localBuffer);
          } catch (final TimeoutException ex) {
            if (this.paused.get()) {
              this.sourceDataLine.stop();
              LOGGER.info("Stopped for data timeout");
              while (this.paused.get() && this.working && !Thread.currentThread().isInterrupted()) {
                Thread.sleep(100);
              }
              if (this.working && !Thread.currentThread().isInterrupted()) {
                fill(localBuffer, SND_LEVEL0);
                writeWholeArray(localBuffer);
                this.sourceDataLine.start();
                LOGGER.info("Work continued");
              }
            }
          }
        }
        LOGGER.info("Main loop completed");
      } catch (InterruptedException ex) {
        LOGGER.info("Interruption");
        Thread.currentThread().interrupt();
      } catch (Exception ex) {
        LOGGER.log(Level.WARNING, "Error in sound line work: " + ex);
      } finally {
        JBBPUtils.closeQuietly(logStream);
        try {
          this.sourceDataLine.stop();
          LOGGER.info("Line stopped");
        } catch (Exception ex) {
          LOGGER.warning("Exception in source line stop: " + ex.getMessage());
        } finally {
          try {
            this.sourceDataLine.close();
            LOGGER.info("Line closed");
          } catch (Exception ex) {
            LOGGER.warning("Exception in source line close: " + ex.getMessage());
          }
        }
        LOGGER.info("Thread stopped");
      }
    }

    @Override
    public void dispose() {
      if (this.working) {
        this.working = false;
        LOGGER.info("Disposing");
        this.thread.interrupt();
        try {
          this.exchanger.exchange(null, 100, TimeUnit.MILLISECONDS);
        } catch (Exception ex) {
          //DO NOTING
        }
        try {
          this.thread.join();
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
        }
      }
    }
  }
}
