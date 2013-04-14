package br.com.is.http.server;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class Test {
  public static void main(String... args) throws InterruptedException, IOException {
    LogManager.getLogManager().getLogger(Logger.GLOBAL_LOGGER_NAME).setLevel(Level.FINE); 
 
    HTTPServer server = new HTTPServer(new InetSocketAddress("localhost", 9999), 10, new File("src/test/resources/testkeys"), "password");

    server.addContext("/test.html", new HTTPContext() {

      @Override
      public void doGet(HTTPRequest req, HTTPResponse resp) {
        Scanner s = null;
        try {
          s = new Scanner(new File("src/test/resources/lorem.txt")).useDelimiter("\\Z");
        }
        catch (FileNotFoundException e1) {
          // TODO Auto-generated catch block
          e1.printStackTrace();
        }
        final String content = s.next();
        s.close();
        //resp.sendRedirect("http://www.google.com.br");
        HTTPSession session = req.getSession();
        OutputStream ou = resp.getOutputStream();

        session.setAttribute("Teste", "Test");
        try {
          ou.write(content.getBytes());
        }
        catch (IOException e) {
          e.printStackTrace();
        }
      }
    });

    server.run();
  }
}