package com.igormaznitsa.zxpoly.components.tapereader.tzx;

import com.igormaznitsa.jbbp.io.JBBPBitInputStream;
import com.igormaznitsa.jbbp.io.JBBPBitOutputStream;

import java.io.IOException;

public class TzxBlockPureData extends AbstractTzxBlock implements DataBlock {
  private final int lengthZeroBitPulse;
  private final int lengthOneBitPulse;
  private final int usedBitsInLastByte;
  private final int pauseAfterBlockMs;
  private final byte[] data;

  public TzxBlockPureData(final JBBPBitInputStream inputStream) throws IOException {
    super(TzxBlock.PURE_DATA_BLOCK.getId());

    this.lengthZeroBitPulse = readWord(inputStream);
    this.lengthOneBitPulse = readWord(inputStream);
    this.usedBitsInLastByte = inputStream.readByte();
    this.pauseAfterBlockMs = readWord(inputStream);

    final int dataLength = readThreeByteValue(inputStream);
    this.data = inputStream.readByteArray(dataLength);
  }

  public int getLengthZeroBitPulse() {
    return lengthZeroBitPulse;
  }

  public int getLengthOneBitPulse() {
    return lengthOneBitPulse;
  }

  public int getUsedBitsInLastByte() {
    return usedBitsInLastByte;
  }

  public int getPauseAfterBlockMs() {
    return pauseAfterBlockMs;
  }

  public byte[] getData() {
    return data;
  }

  @Override
  public void write(final JBBPBitOutputStream outputStream) throws IOException {
    super.write(outputStream);

    writeWord(outputStream, this.lengthZeroBitPulse);
    writeWord(outputStream, this.lengthOneBitPulse);
    outputStream.write(this.usedBitsInLastByte);
    writeWord(outputStream, this.pauseAfterBlockMs);

    writeThreeByteValue(outputStream, this.data.length);
    outputStream.write(this.data);
  }
}
