package br.com.is.http.server.encoder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

public class GZIPEncoder implements Encoder {
  private boolean finished = false;
  private ByteArrayOutputStream os = new ByteArrayOutputStream();
  private GZIPOutputStream gos;
  
  public GZIPEncoder() {
    try {
      gos = new GZIPOutputStream(os);
    }
    catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }
  
  @Override
  public void compress(byte[] source, int offset, int length) {
    try {
      gos.write(source, offset, length);
    }
    catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  @Override
  public boolean isFinished() {
    return finished;
  }

  @Override
  public String getType() {
    return "gzip";
  }

  @Override
  public byte[] finish() {
    if (!finished) {
      finished = true;
      try {
        gos.finish();
        gos.close();
        os.close();
      }
      catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    return os.toByteArray();
  }
}
