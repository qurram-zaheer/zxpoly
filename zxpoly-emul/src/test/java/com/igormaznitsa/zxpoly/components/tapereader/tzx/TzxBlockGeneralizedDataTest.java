package com.igormaznitsa.zxpoly.components.tapereader.tzx;

import com.igormaznitsa.jbbp.io.JBBPBitInputStream;
import com.igormaznitsa.jbbp.io.JBBPBitOutputStream;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.Assert.*;

public class TzxBlockGeneralizedDataTest {
  @Test
  public void testDocCase_1() throws IOException {
    final byte[] testData = new byte[]{
            0x3B, 0x00, 0x00, 0x00,
            (byte) 0xE8, 0x03,
            0x02, 0x00, 0x00, 0x00,
            0x02,
            0x02,
            (byte) 0x98, 0x00, 0x00, 0x00,
            0x02,
            0x02,
            0, 0x78, 0x08, 0x00, 0x00,
            0, (byte) 0x9B, 0x02, (byte) 0xDF, 0x02,
            0, (byte) 0x7F, (byte) 0x1F,
            1, 0x01, 0x00,
            0, 0x57, 0x03, 0x57, 0x03,
            0, (byte) 0xAE, 0x06, (byte) 0xAE, 0x06,
            0x00,
            0x03,
            0x4A,
            0x50,
            0x53,
            0x50,
            0x20,
            0x20,
            0x20,
            0x20,
            0x20,
            0x20,
            0x00,
            0x1B,
            0x00,
            0x40,
            0x00,
            (byte) 0x80,
            (byte) 0xC1};

    final JBBPBitInputStream jbbpBitInputStream = new JBBPBitInputStream(new ByteArrayInputStream(testData));
    final TzxBlockGeneralizedData read = new TzxBlockGeneralizedData(jbbpBitInputStream);
    assertFalse(jbbpBitInputStream.hasAvailableData());
    assertEquals(19, read.getDataStream().length);
    assertArrayEquals(testData, asByteArray(read));
  }

  private byte[] asByteArray(final AbstractTzxBlock block) throws IOException {
    final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    final JBBPBitOutputStream jbbpBitOutputStream = new JBBPBitOutputStream(outputStream);
    block.write(jbbpBitOutputStream);
    jbbpBitOutputStream.flush();
    jbbpBitOutputStream.close();

    byte[] saved = outputStream.toByteArray();
    byte[] withoutId = new byte[saved.length - 1];
    System.arraycopy(saved, 1, withoutId, 0, withoutId.length);

    return withoutId;
  }

  @Test
  public void testDocCase_2() throws IOException {
    final byte[] testData = new byte[]{
            0x6B, 0x00, 0x00, 0x00,
            (byte) 0xE8, 0x03,
            0x00, 0x00, 0x00, 0x00,
            0x00,
            0x00,
            (byte) 0x98, 0x00, 0x00, 0x00,
            0x12,
            0x02,

            0x03, 0x12, 0x02, 0x08, 0x02, 0x12, 0x02, 0x08, 0x02, 0x12, 0x02, 0x08, 0x02, 0x12, 0x02, 0x51, 0x12, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x03, 0x12, 0x02, 0x08, 0x02, 0x12, 0x02, 0x08, 0x02, 0x12, 0x02, 0x08, 0x02, 0x12, 0x02, 0x08, 0x02, 0x12, 0x02, 0x08, 0x02, 0x12, 0x02, 0x08, 0x02, 0x12, 0x02, 0x08, 0x02, 0x12, 0x02, 0x08, 0x02, 0x12, 0x02, 0x51, 0x12,

            0x00,
            0x03,
            0x4A,
            0x50,
            0x53,
            0x50,
            0x20,
            0x20,
            0x20,
            0x20,
            0x20,
            0x20,
            0x00,
            0x1B,
            0x00,
            0x40,
            0x00,
            (byte) 0x80,
            (byte) 0xC1
    };

    final JBBPBitInputStream jbbpBitInputStream = new JBBPBitInputStream(new ByteArrayInputStream(testData));
    final TzxBlockGeneralizedData read = new TzxBlockGeneralizedData(jbbpBitInputStream);
    assertFalse(jbbpBitInputStream.hasAvailableData());
    assertEquals(19, read.getDataStream().length);
    assertArrayEquals(testData, asByteArray(read));
  }
}