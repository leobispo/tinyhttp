package br.com.is.http.server;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

public class Test {
  public static void main(String... args) throws InterruptedException, IOException {
    System.out.println("Starting HTTP Class");
    HTTPServer server = new HTTPServer(new InetSocketAddress("localhost", 9999), 10, HTTPServer.Type.HTTP);

    server.addContext("/test.html", new HTTPContext() {

      @Override
      protected void doGet(HTTPRequest req, HTTPResponse resp) {
        /*
        OutputStream ou = resp.getOutputStream();

        try {
          ou.write("abc".getBytes());
          ou.flush();
        }
        catch (IOException e) {
          e.printStackTrace();
        }
        */
      }
    });

    server.run();
    while (true) {
      Thread.sleep(1000);
    }
  }
}
