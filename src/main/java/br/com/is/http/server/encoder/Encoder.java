package br.com.is.http.server.encoder;

public interface Encoder {
  public void compress(byte[] source, int offset, int length);
  
  public boolean isFinished();
  
  public String getType();
  
  public byte[] finish();
}
