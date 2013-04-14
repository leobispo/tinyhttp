package br.com.is.http.server.test;

import static org.junit.Assert.assertEquals;

import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLConnection;
import java.security.KeyStore;
import java.util.Scanner;
import java.util.zip.GZIPInputStream;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import org.junit.Test;

import br.com.is.http.server.HTTPContext;
import br.com.is.http.server.HTTPRequest;
import br.com.is.http.server.HTTPResponse;
import br.com.is.http.server.HTTPServer;

public class HTTPTest {
  static {  
    javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier(new javax.net.ssl.HostnameVerifier() {  
      public boolean verify(String hostname, javax.net.ssl.SSLSession sslSession) {  
        return true; 
      }  
    });  
  } 

  @SuppressWarnings("resource")
  @Test
  public void testGET() throws Exception {
    Scanner s = new Scanner(new File("src/test/resources/lorem.txt")).useDelimiter("\\Z");
    final String content = s.next();
    s.close();
    
    HTTPServer server = new HTTPServer(new InetSocketAddress("localhost", 9999), 10);

    server.addContext("/test.html", new HTTPContext() {
      @Override
      public void doGet(HTTPRequest req, HTTPResponse resp) {
        OutputStream os = resp.getOutputStream();
        try {
          os.write(req.getParameter("param1").getBytes());
          os.flush();
          os.write(req.getParameter("param2").getBytes());
          os.flush();
          os.write(content.getBytes());
          
          os.flush();
        }
        catch (IOException e) {
          e.printStackTrace();
        }
      }
    });
 
    Thread thread = new Thread(server);
    thread.start();
    
    Thread.sleep(100);

    URL url = new URL("http://localhost:9999/test.html?param1=test&param2=this+is+a+test");
    URLConnection conn = url.openConnection();
    InputStream is = conn.getInputStream();
    
    s = new Scanner(is).useDelimiter("\\A");
    final String result = s.hasNext() ? s.next() : "";
    is.close();
    s.close();
 
    assertEquals("testthis is a test" + content, result);

    server.stop(10);
  }
  
  @SuppressWarnings("resource")
  @Test
  public void testPOST() throws Exception {
    Scanner s = new Scanner(new File("src/test/resources/lorem.txt")).useDelimiter("\\Z");
    final String content = s.next();
    s.close();
    
    HTTPServer server = new HTTPServer(new InetSocketAddress("localhost", 9999), 10);

    server.addContext("/test.html", new HTTPContext() {
      @Override
      public void doPost(HTTPRequest req, HTTPResponse resp) {
        OutputStream os = resp.getOutputStream();
        try {
          os.write(req.getParameter("param1").getBytes());
          os.flush();
          os.write(req.getParameter("param2").getBytes());
          os.flush();
          os.write(content.getBytes());
          
          os.flush();
        }
        catch (IOException e) {
          e.printStackTrace();
        }
      }
    });
 
    Thread thread = new Thread(server);
    thread.start();
    
    Thread.sleep(100);

    String urlParameters = "param1=test&param2=this+is+a+test";
    String request = "http://localhost:9999/test.html";
    URL url = new URL(request); 
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();           
    conn.setDoOutput(true);
    conn.setDoInput(true);
    conn.setInstanceFollowRedirects(false); 
    conn.setRequestMethod("POST"); 
    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded"); 
    conn.setRequestProperty("charset", "utf-8");
    conn.setRequestProperty("Content-Length", "" + Integer.toString(urlParameters.getBytes().length));
    conn.setUseCaches(false);

    DataOutputStream wr = new DataOutputStream(conn.getOutputStream());
    wr.writeBytes(urlParameters);
    wr.flush();
    wr.close();
    conn.disconnect();
    
    InputStream is = conn.getInputStream();
    
    s = new Scanner(is).useDelimiter("\\A");
    final String result = s.hasNext() ? s.next() : "";
    is.close();
    s.close();
    
    assertEquals("testthis is a test" + content, result);
    server.stop(10);
  }
  
  @Test
  public void testGETGZIP() throws Exception {
    Scanner s = new Scanner(new File("src/test/resources/lorem.txt")).useDelimiter("\\Z");
    final String content = s.next();
    s.close();
    
    HTTPServer server = new HTTPServer(new InetSocketAddress("localhost", 9999), 10);

    server.addContext("/test.html", new HTTPContext() {
      @Override
      public void doGet(HTTPRequest req, HTTPResponse resp) {
        OutputStream os = resp.getOutputStream();
        try {
          os.write(req.getParameter("param1").getBytes());
          os.flush();
          os.write(req.getParameter("param2").getBytes());
          os.flush();
          os.write(content.getBytes());
          
          os.flush();
        }
        catch (IOException e) {
          e.printStackTrace();
        }
      }
    });
 
    Thread thread = new Thread(server);
    thread.start();
    
    Thread.sleep(100);

    URL url = new URL("http://localhost:9999/test.html?param1=test&param2=this+is+a+test");
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setRequestProperty("Accept-Encoding", "gzip");
    
    assertEquals("gzip", conn.getContentEncoding());

    InputStream is = new GZIPInputStream(conn.getInputStream());
 
    int read = 0;
    byte[] bytes = new byte[1024];
 
    StringBuilder sb = new StringBuilder();
    
    try {
      while ((read = is.read(bytes)) != -1) {
        sb.append(new String(bytes, 0, read));
      }
    }
    catch (EOFException e) {} // For some strange reason, GZIPInputStream is not working correctly together with HttpURLConnection
    
    assertEquals("testthis is a test" + content, sb.toString());
    is.close();
    server.stop(10);
  }

  @Test
  public void testPOSTGZIP() throws Exception {
    Scanner s = new Scanner(new File("src/test/resources/lorem.txt")).useDelimiter("\\Z");
    final String content = s.next();
    s.close();
    
    HTTPServer server = new HTTPServer(new InetSocketAddress("localhost", 9999), 10);

    server.addContext("/test.html", new HTTPContext() {
      @Override
      public void doPost(HTTPRequest req, HTTPResponse resp) {
        OutputStream os = resp.getOutputStream();
        try {
          os.write(req.getParameter("param1").getBytes());
          os.flush();
          os.write(req.getParameter("param2").getBytes());
          os.flush();
          os.write(content.getBytes());
          
          os.flush();
        }
        catch (IOException e) {
          e.printStackTrace();
        }
      }
    });
 
    Thread thread = new Thread(server);
    thread.start();
    
    Thread.sleep(100);

    String urlParameters = "param1=test&param2=this+is+a+test";
    String request = "http://localhost:9999/test.html";
    URL url = new URL(request); 
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();           
    conn.setDoOutput(true);
    conn.setDoInput(true);
    conn.setInstanceFollowRedirects(false); 
    conn.setRequestMethod("POST"); 
    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded"); 
    conn.setRequestProperty("charset", "utf-8");
    conn.setRequestProperty("Accept-Encoding", "gzip");
    conn.setRequestProperty("Content-Length", "" + Integer.toString(urlParameters.getBytes().length));
    conn.setUseCaches(false);

    DataOutputStream wr = new DataOutputStream(conn.getOutputStream());
    wr.writeBytes(urlParameters);
    wr.flush();
    wr.close();
    conn.disconnect();
    
    assertEquals("gzip", conn.getContentEncoding());

    InputStream is = new GZIPInputStream(conn.getInputStream());
 
    int read = 0;
    byte[] bytes = new byte[1024];
 
    StringBuilder sb = new StringBuilder();
    
    try {
      while ((read = is.read(bytes)) != -1) {
        sb.append(new String(bytes, 0, read));
      }
    }
    catch (EOFException e) {} // For some strange reason, GZIPInputStream is not working correctly together with HttpURLConnection
    
    assertEquals("testthis is a test" + content, sb.toString());
    is.close();
    server.stop(10);
  }

  @SuppressWarnings("resource")
  @Test
  public void testGETHTTPS() throws Exception {
    Scanner s = new Scanner(new File("src/test/resources/lorem.txt")).useDelimiter("\\Z");
    final String content = s.next();
    s.close();
    
    HTTPServer server = new HTTPServer(new InetSocketAddress("localhost", 9999), 10, new File("src/test/resources/testkeys"), "password");

    server.addContext("/test.html", new HTTPContext() {
      @Override
      public void doGet(HTTPRequest req, HTTPResponse resp) {
        OutputStream os = resp.getOutputStream();
        try {
          os.write(req.getParameter("param1").getBytes());
          os.flush();
          os.write(req.getParameter("param2").getBytes());
          os.flush();
          os.write(content.getBytes());
          
          os.flush();
        }
        catch (IOException e) {
          e.printStackTrace();
        }
      }
    });
 
    Thread thread = new Thread(server);
    thread.start();
    
    Thread.sleep(100);

    char[] passphrase = "password".toCharArray();

    KeyStore ks = KeyStore.getInstance("JKS");
    ks.load(new FileInputStream("src/test/resources/testkeys"), passphrase);

    KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
    kmf.init(ks, passphrase);

    TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
    tmf.init(ks);

    SSLContext sslContext = SSLContext.getInstance("TLS");
    sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

    SSLSocketFactory factory = sslContext.getSocketFactory();
    HttpsURLConnection.setDefaultSSLSocketFactory(factory);
    SSLSocket socket = (SSLSocket) factory.createSocket("localhost", 9999);
    String[] suites = socket.getSupportedCipherSuites();
    socket.setEnabledCipherSuites(suites);
    socket.startHandshake();
    
    URL url = new URL("https://localhost:9999/test.html?param1=test&param2=this+is+a+test");
    HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
    InputStream is = conn.getInputStream();
    
    s = new Scanner(is).useDelimiter("\\A");
    final String result = s.hasNext() ? s.next() : "";
    is.close();
    s.close();
 
    assertEquals("testthis is a test" + content, result);

    conn.disconnect();
    server.stop(10);
  }
  
  @Test
  public void testPOSTHTTPS() throws Exception {
    Scanner s = new Scanner(new File("src/test/resources/lorem.txt")).useDelimiter("\\Z");
    final String content = s.next();
    s.close();
    
    HTTPServer server = new HTTPServer(new InetSocketAddress("localhost", 9999), 10, new File("src/test/resources/testkeys"), "password");

    server.addContext("/test.html", new HTTPContext() {
      @Override
      public void doPost(HTTPRequest req, HTTPResponse resp) {
        OutputStream os = resp.getOutputStream();
        try {
          os.write(req.getParameter("param1").getBytes());
          os.flush();
          os.write(req.getParameter("param2").getBytes());
          os.flush();
          os.write(content.getBytes());
          
          os.flush();
        }
        catch (IOException e) {
          e.printStackTrace();
        }
      }
    });
 
    Thread thread = new Thread(server);
    thread.start();
    
    Thread.sleep(100);

    char[] passphrase = "password".toCharArray();

    KeyStore ks = KeyStore.getInstance("JKS");
    ks.load(new FileInputStream("src/test/resources/testkeys"), passphrase);

    KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
    kmf.init(ks, passphrase);

    TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
    tmf.init(ks);

    SSLContext sslContext = SSLContext.getInstance("TLS");
    sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

    SSLSocketFactory factory = sslContext.getSocketFactory();
    HttpsURLConnection.setDefaultSSLSocketFactory(factory);
    SSLSocket socket = (SSLSocket) factory.createSocket("localhost", 9999);
    String[] suites = socket.getSupportedCipherSuites();
    socket.setEnabledCipherSuites(suites);
    socket.startHandshake();

    String urlParameters = "param1=test&param2=this+is+a+test";
    String request = "https://localhost:9999/test.html";
    URL url = new URL(request); 
    HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();           
    conn.setDoOutput(true);
    conn.setDoInput(true);
    conn.setInstanceFollowRedirects(false); 
    conn.setRequestMethod("POST"); 
    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded"); 
    conn.setRequestProperty("charset", "utf-8");
    conn.setRequestProperty("Accept-Encoding", "gzip");
    conn.setRequestProperty("Content-Length", "" + Integer.toString(urlParameters.getBytes().length));
    conn.setUseCaches(false);

    DataOutputStream wr = new DataOutputStream(conn.getOutputStream());
    wr.writeBytes(urlParameters);
    wr.flush();
    wr.close();
    conn.disconnect();
    
    assertEquals("gzip", conn.getContentEncoding());

    InputStream is = new GZIPInputStream(conn.getInputStream());
 
    int read = 0;
    byte[] bytes = new byte[1024];
 
    StringBuilder sb = new StringBuilder();
    
    try {
      while ((read = is.read(bytes)) != -1) {
        sb.append(new String(bytes, 0, read));
      }
    }
    catch (EOFException e) {} // For some strange reason, GZIPInputStream is not working correctly together with HttpURLConnection
    
    assertEquals("testthis is a test" + content, sb.toString());
    is.close();
    conn.disconnect();
    server.stop(10);
  }
}
