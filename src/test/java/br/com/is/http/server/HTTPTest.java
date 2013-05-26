package br.com.is.http.server;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.security.KeyStore;
import java.util.Scanner;
import java.util.zip.GZIPInputStream;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import br.com.is.http.server.HTTPContext;
import br.com.is.http.server.HTTPRequest;
import br.com.is.http.server.HTTPResponse;
import br.com.is.http.server.HTTPServer;
import br.com.is.http.server.annotation.Context;
import br.com.is.http.server.annotation.GET;
import br.com.is.http.server.annotation.POST;

@Context(urlPattern="/test/annotation.html")
class AnnotationTest {
  @GET
  @POST
  public void process(HTTPRequest req, HTTPResponse resp) {
    try {
      @SuppressWarnings("resource")
      final Scanner scanner = (new Scanner(new File("src/test/resources/lorem.txt"))).useDelimiter("\\Z");

      final String content = scanner.next();
      scanner.close();

      final OutputStream os = resp.getOutputStream();
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
    catch (FileNotFoundException e1) {}
  }
}

public class HTTPTest {
  private String     content = null;
  private HTTPServer http    = null;
  private HTTPServer https   = null;
  
  static {  
    javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier(new javax.net.ssl.HostnameVerifier() {  
      public boolean verify(String hostname, javax.net.ssl.SSLSession sslSession) {  
        return true; 
      }  
    });  
  }

  @Before
  @SuppressWarnings("resource")
  public final void setUp() throws Exception { 
    http  = new HTTPServer(new InetSocketAddress("localhost", 9999), 10);
    https = new HTTPServer(new InetSocketAddress("localhost", 9991), 10, new File("src/test/resources/testkeys"), "password");

    final Scanner scanner = (new Scanner(new File("src/test/resources/lorem.txt"))).useDelimiter("\\Z");
    content = scanner.next();
    scanner.close();
    
    final HTTPContext ctx = new HTTPContext() {
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
    };
    
    http.addContext("/test.html", ctx);
    https.addContext("/test.html", ctx);
    
    final HTTPContext ctx1 = new HTTPContext() {
      @Override
      public void doGet(HTTPRequest req, HTTPResponse resp) {
        OutputStream os = resp.getOutputStream();
        try {
          os.write(content.getBytes());
          os.flush();
        }
        catch (IOException e) {
          e.printStackTrace();
        }
      }
      
      @Override
      public void doPost(HTTPRequest req, HTTPResponse resp) {
        OutputStream os = resp.getOutputStream();
        try {
          os.write(content.getBytes());
          os.flush();
        }
        catch (IOException e) {
          e.printStackTrace();
        }
      }
    };
    
    http.addContext("/test1.html", ctx1);
    https.addContext("/test1.html", ctx1);
    
    final HTTPContext ctxRedirect = new HTTPContext() {
      @Override
      public void doGet(HTTPRequest req, HTTPResponse resp) {
        resp.sendRedirect("test1.html");
      }
      
      @Override
      public void doPost(HTTPRequest req, HTTPResponse resp) {
        resp.sendRedirect("test1.html");
      }
    };
   
    http.addContext("/redirect.html", ctxRedirect);
    https.addContext("/redirect.html", ctxRedirect);
    
    (new Thread(http)).start();
    (new Thread(https)).start();
    
    Thread.sleep(1000);
  }
  
  @After
  public final void tearDown() throws Exception {
    http.stop(10);
    https.stop(10);
  }

  @Test
  public void testGET() throws Exception {
    final URL url = new URL("http://localhost:9999/test.html?param1=test&param2=this+is+a+test");
    final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setAllowUserInteraction(false);
    conn.disconnect();
    
    final InputStream is = conn.getInputStream();
    
    assertEquals("testthis is a test" + content, readInputStream(is));
    is.close();
  }

  @Test
  public void testPOST() throws Exception {
    final String params = "param1=test&param2=this+is+a+test";

    final URL url = new URL("http://localhost:9999/test.html"); 
    final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    
    conn.setDoInput(true);
    conn.setDoOutput(true);
    conn.setRequestMethod("POST"); 
    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded"); 
    conn.setRequestProperty("Content-Length", "" + Integer.toString(params.getBytes().length));

    conn.getOutputStream().write(params.getBytes());
    conn.disconnect();

    final InputStream is = conn.getInputStream();
    
    assertEquals("testthis is a test" + content, readInputStream(is));
    is.close();
  }
  
  @Test
  public void testAnnotationGET() throws Exception {
    final URL url = new URL("http://localhost:9999/test/annotation.html?param1=test&param2=this+is+a+test");
    final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setAllowUserInteraction(false);
    conn.disconnect();
    
    final InputStream is = conn.getInputStream();
    
    assertEquals("testthis is a test" + content, readInputStream(is));
    is.close();
  }
  
  @Test
  public void testAnnotationPOST() throws Exception {
    final String params = "param1=test&param2=this+is+a+test";

    final URL url = new URL("http://localhost:9999/test/annotation.html"); 
    final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    
    conn.setDoInput(true);
    conn.setDoOutput(true);
    conn.setRequestMethod("POST"); 
    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded"); 
    conn.setRequestProperty("Content-Length", "" + Integer.toString(params.getBytes().length));

    conn.getOutputStream().write(params.getBytes());
    conn.disconnect();

    final InputStream is = conn.getInputStream();
    
    assertEquals("testthis is a test" + content, readInputStream(is));
    is.close();
  }
  
  @Test
  public void testGETGZIP() throws Exception {
    final URL url = new URL("http://localhost:9999/test.html?param1=test&param2=this+is+a+test");
    final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    
    conn.setRequestProperty("Accept-Encoding", "gzip");
    conn.disconnect();
    
    assertEquals("gzip", conn.getContentEncoding());

    final InputStream is = new GZIPInputStream(conn.getInputStream());
    
    assertEquals("testthis is a test" + content, readInputStream(is));
    is.close();
  }

  @Test
  public void testPOSTGZIP() throws Exception {
    final String params = "param1=test&param2=this+is+a+test";

    final URL url = new URL("http://localhost:9999/test.html"); 
    final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    
    conn.setDoInput(true);
    conn.setDoOutput(true);
    conn.setRequestMethod("POST");
    conn.setRequestProperty("Accept-Encoding", "gzip");
    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded"); 
    conn.setRequestProperty("Content-Length", "" + Integer.toString(params.getBytes().length));

    conn.getOutputStream().write(params.getBytes());
    conn.disconnect();

    assertEquals("gzip", conn.getContentEncoding());

    final InputStream is = new GZIPInputStream(conn.getInputStream());
    
    assertEquals("testthis is a test" + content, readInputStream(is));
    is.close();
  }

  @Test
  public void testGETHTTPS() throws Exception {
    startHTTPSContext();
    final URL url = new URL("https://localhost:9991/test.html?param1=test&param2=this+is+a+test");

    final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setAllowUserInteraction(false);
    
    final InputStream is = conn.getInputStream();
    
    assertEquals("testthis is a test" + content, readInputStream(is));

    is.close();
  }
  
  @Test
  public void testPOSTHTTPS() throws Exception {
    startHTTPSContext();

    final String params = "param1=test&param2=this+is+a+test";

    final URL url = new URL("https://localhost:9991/test.html"); 
    final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    
    conn.setDoInput(true);
    conn.setDoOutput(true);
    conn.setRequestMethod("POST"); 
    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded"); 
    conn.setRequestProperty("Content-Length", "" + Integer.toString(params.getBytes().length));

    conn.getOutputStream().write(params.getBytes());
    conn.disconnect();

    final InputStream is = conn.getInputStream();
    
    assertEquals("testthis is a test" + content, readInputStream(is));
    is.close();
  }
  
  @Test
  public void testRedirectHTTP() throws Exception {
    final URL url = new URL("http://localhost:9999/redirect.html");
    final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setAllowUserInteraction(false);
    conn.disconnect();
    
    final InputStream is = conn.getInputStream();
    
    assertEquals(content, readInputStream(is));
    is.close();
  }
  
  @Test
  public void testRedirectHTTPS() throws Exception {
    startHTTPSContext();

    final String params = "param1=test&param2=this+is+a+test";

    final URL url = new URL("https://localhost:9991/redirect.html"); 
    final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    
    conn.setDoInput(true);
    conn.setDoOutput(true);
    conn.setRequestMethod("POST"); 
    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded"); 
    conn.setRequestProperty("Content-Length", "" + Integer.toString(params.getBytes().length));

    conn.getOutputStream().write(params.getBytes());
    conn.disconnect();

    final InputStream is = conn.getInputStream();
    
    assertEquals(content, readInputStream(is));
    is.close();
  }
  
  private final void startHTTPSContext() throws Exception {
    char[] passphrase = "password".toCharArray();

    final KeyStore ks = KeyStore.getInstance("JKS");
    ks.load(new FileInputStream("src/test/resources/testkeys"), passphrase);

    KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
    kmf.init(ks, passphrase);

    TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
    tmf.init(ks);

    SSLContext sslContext = SSLContext.getInstance("TLS");
    sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

    HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
  }

  private final String readInputStream(final InputStream is) throws Exception {
    final StringBuilder sb = new StringBuilder();

    int read = 0;
    final byte[] bytes = new byte[1024];

    try {
      while ((read = is.read(bytes)) != -1)
        sb.append(new String(bytes, 0, read));
    }
    catch (IOException e) {}

    return sb.toString();
  }

  //TODO: Test Static Context
  //TODO: Test Static Context Range
  //TODO: Test Static Context Directory
  //TODO: Test ETag
  //TODO: Test Multipart
}
