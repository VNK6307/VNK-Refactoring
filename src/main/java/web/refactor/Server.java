package web.refactor;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private final List<String> validPaths;
    private static final int THREAD_POOL_SIZE = 64;
    private final ExecutorService threadPool;

    public Server(List<String> validPaths) {
        this.threadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        this.validPaths = validPaths;
    }

    public void start(int port) {
        try (final var serverSocket = new ServerSocket(port)) {
            while (true) {
                try {
                    final var socket = serverSocket.accept();
                    threadPool.execute(() -> {
                        connection(socket);
                    });
                } catch (IOException e) {
                }
            }
        } catch (IOException e) {
        }
    }

    private void connection(Socket socket) {
        String thread = Thread.currentThread().getName();
        System.out.println("Connection to thread " + thread);
        try (
                final var in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                final var out = new BufferedOutputStream(socket.getOutputStream())
        ) {
            handleRequest(in, out);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleRequest(BufferedReader in, BufferedOutputStream out) throws IOException {
        final var requestLine = in.readLine();
        final var parts = requestLine.split(" ");

        if (parts.length != 3) {
            out.close();
            return;
        }

        final var path = parts[1];
        if (!validPaths.contains(path)) {
            sendNotFoundResponse(out);
            return;
        }
        final var filePath = Path.of(".", "public", path);
        final var mimeType = Files.probeContentType(filePath);

        // special case for classic
        if (path.equals("/classic.html")) {
            classicRequest(out, filePath, mimeType);
        } else {
            regularRequest(out, filePath, mimeType);
        }
        out.close();
    }


    private void sendNotFoundResponse(BufferedOutputStream out) throws IOException {
        String response = "HTTP/1.1 404 Not Found\r\n" +
                "Content-Length: 0\r\n" +
                "Connection: close\r\n" +
                "\r\n";
        out.write(response.getBytes());
        out.flush();
    }

    private void sendResponse(BufferedOutputStream out, String mimeType, byte[] content) throws IOException {
        String response = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: " + mimeType + "\r\n" +
                "Content-Length: " + content.length + "\r\n" +
                "Connection: close\r\n" +
                "\r\n";
        out.write(response.getBytes());
        out.write(content);
        out.flush();
    }


    private void classicRequest(BufferedOutputStream out, Path filePath, String mimeType) throws IOException {
        String template = Files.readString(filePath);
        String content = template.replace("{time}", LocalDateTime.now().toString());
        sendResponse(out, mimeType, content.getBytes());
    }

    private void regularRequest(BufferedOutputStream out, Path filePath, String mimeType) throws IOException {
        sendResponse(out, mimeType, Files.readAllBytes(filePath));
    }


}// class
