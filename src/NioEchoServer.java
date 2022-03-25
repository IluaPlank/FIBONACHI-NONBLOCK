import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NioEchoServer {
    private static final Map<SocketChannel, ByteBuffer> sockets = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        //  Занимаем порт, определяя серверный сокет
        final ServerSocketChannel serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress(8080));
        serverChannel.configureBlocking(false);

        Selector selector = Selector.open();
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        //прослушиваем все сообщения о подключении

        log("Server start");
        try {
            while (true) {
                selector.select(); // Blocking call, but only one for everything
                for (SelectionKey key : selector.selectedKeys()) {
//                    подключение было,получени всех ивентов
                    if (key.isValid()) {
                        try {
                            if (key.isAcceptable()) {
//                                новое сокет соединение
                                SocketChannel socketChannel = serverChannel.accept(); // Non blocking, never null
                                socketChannel.configureBlocking(false);
                                log("Connected " + socketChannel.getRemoteAddress());
                                sockets.put(socketChannel, ByteBuffer.allocate(1000)); // Allocating buffer for socket channel
                                socketChannel.register(selector, SelectionKey.OP_READ);
                            } else if (key.isReadable()) {
//                                читать сообщение
                                SocketChannel socketChannel = (SocketChannel) key.channel();
                                ByteBuffer buffer = sockets.get(socketChannel);
                                int bytesRead = socketChannel.read(buffer); // Reading, non-blocking call
                                log("Reading from " + socketChannel.getRemoteAddress() + ", bytes read=" + bytesRead);

                                // Detecting connection closed from client side
                                if (bytesRead == -1) {
                                    log("Connection closed " + socketChannel.getRemoteAddress());
                                    sockets.remove(socketChannel);
                                    socketChannel.close();
                                }

                                // Detecting end of the message
                                if (bytesRead > 0 && buffer.get(buffer.position() - 1) == '\n') {
                                    socketChannel.register(selector, SelectionKey.OP_WRITE);
                                }
                            } else if (key.isWritable()) {
//                                писать сообщение
                                SocketChannel socketChannel = (SocketChannel) key.channel();
                                ByteBuffer buffer = sockets.get(socketChannel);

                                // Reading client message from buffer
                                buffer.flip();
                                String clientMessage = new String(buffer.array(), buffer.position(), buffer.limit());
                                System.out.println(clientMessage);
                                int number = Integer.parseInt(clientMessage.split("\r")[0]);
                                BigDecimal fibonachi = estimate(number);
                                // Building response
                                String response = fibonachi + "\r\n";

                                // Writing response to buffer
                                buffer.clear();
                                buffer.put(ByteBuffer.wrap(response.getBytes()));
                                buffer.flip();

                                int bytesWritten = socketChannel.write(buffer); // woun't always write anything
                                log("Writing to " + socketChannel.getRemoteAddress() + ", bytes writteb=" + bytesWritten);
                                if (!buffer.hasRemaining()) {
                                    buffer.compact();
                                    socketChannel.register(selector, SelectionKey.OP_READ);
                                }
                            }
                        } catch (IOException e) {
                            log("error " + e.getMessage());
                        }
                    }
                }

                selector.selectedKeys().clear();
            }
        } catch (IOException err) {
            System.out.println(err.getMessage());
        } finally {
            serverChannel.close();
        }
    }

    private static final MathContext mathContext = new MathContext(20, RoundingMode.HALF_UP);
    private static final double sqrt5 = Math.sqrt(5);
    public static final BigDecimal Sqrt5 = new BigDecimal(sqrt5, mathContext);
    public static final BigDecimal Phi = new BigDecimal((1 + sqrt5) / 2, mathContext);

    public static BigDecimal estimate(int number) {
        return Phi.pow(number, mathContext).divide(Sqrt5, mathContext);
    }

    private static void log(String message) {
        System.out.println("[" + Thread.currentThread().getName() + "] " + message);
    }
}