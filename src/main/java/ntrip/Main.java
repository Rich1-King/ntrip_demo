package ntrip;

import com.sun.xml.internal.ws.api.model.wsdl.WSDLOutput;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;

/**
 * @author sunchong
 */
public class Main {

    public static void main(String[] args) throws IOException, InterruptedException {
        SocketChannel socketChannel = SocketChannel.open(new InetSocketAddress("119.3.136.126", 8001));
        socketChannel.configureBlocking(false);

//        String value = "GET / HTTP/1.0\r\nUser-Agent:NTRIP GNRadio/1.4\r\nAccept:*/*\r\nConnection:close";

        Base64.Encoder encoder = Base64.getEncoder();
        String authorization = encoder.encodeToString("aabb1780:01972527".getBytes());
        String value =
            "GET /RTCM33 HTTP/1.0\r\nUser-Agent:NTRIP GNRadio/1.4\r\nAccept: */*\r\nAuthorization:Basic "
                + authorization + "\r\n";
        socketChannel.write(ByteBuffer.wrap(value.getBytes(StandardCharsets.UTF_8)));
        int readTime = 1;
        int noData = 1;
        boolean canSend = false;
        do {
            ByteBuffer readBuffer = ByteBuffer.allocate(1024);
            String response = "";
            int length = 0;
            while ((length = socketChannel.read(readBuffer)) > 0) {
                if (noData >= 1) {
                    noData--;
                }
                byte[] bytes = readBuffer.array();
                if (readTime == 1) {
                    response = new String(bytes, 0, length);
                } else {
                    byte[][] tempBytes = spilt(bytes, new byte[] {-45, 0});
                    for (byte[] select : tempBytes) {
                        response = bytesToHexString(select);
                        System.out.println(response);
                    }
                }
                readTime++;
                readBuffer.clear();
            }
            if (noData > 10 || canSend || response.contains("ICY 200 OK")) {
                String gps = getGga();
                System.out.println("发送数据:" + gps);
                socketChannel.write(ByteBuffer.wrap(gps.getBytes(StandardCharsets.UTF_8)));
//                canSend = true;
            }
            noData++;
            System.out.println("read over");
            Thread.sleep(3000L);
        } while (true);
    }

    private static byte[][] spilt(byte[] src, byte[] splitValue) {
        int len = splitValue.length;
        int p = 0;
        List<SplitData> data = new ArrayList<>();
        int begin = 0;
        int length = len;
        for (byte b : src) {
            if (p < len) {
                if (b == splitValue[p]) {
                    if (p == len - 1 && length > len) {
                        SplitData splitData = new SplitData();
                        splitData.setLen(length);
                        splitData.setBegin(begin);
                        begin += length;
                        data.add(splitData);
                        length = len;
                    }
                    p++;
                } else {
                    length++;
                    p = 0;
                }
            } else {
                p = 0;
                length++;
            }
        }

        byte[][] result = new byte[data.size()][];
        int i = 0;
        for (SplitData tempData : data) {
            byte[] tempByte = new byte[tempData.getLen()];
            System.arraycopy(src, tempData.getBegin(), tempByte, 0, tempData.getLen());
            result[i++] = tempByte;
        }
        return result;
    }

    static class SplitData {
        private Integer len;
        private Integer begin;

        public Integer getLen() {
            return len;
        }

        public void setLen(Integer len) {
            this.len = len;
        }

        public Integer getBegin() {
            return begin;
        }

        public void setBegin(Integer begin) {
            this.begin = begin;
        }
    }

    private static String bytesToHexString(byte[] src) {
        StringBuilder stringBuilder = new StringBuilder("");
        if (src == null || src.length <= 0) {
            return null;
        }
        for (int i = 0; i < src.length; i++) {
            int v = src[i] & 0xFF;
            String hv = Integer.toHexString(v);
            if (hv.length() < 2) {
                stringBuilder.append(0);
            }
            stringBuilder.append(hv);
        }
        return stringBuilder.toString();
    }

    private static String getGga() {
        String ggaTpl = "GPGGA,%s,%s,N,%s,E,4,07,1.5,%s,M,8.942,M,0.7,0016";

        String gga = String.format(ggaTpl, getTime(), "3112.3856", "12123.5312", "6.157");
        int x = 0;
        int y;
        for (int i = 0; i < gga.length(); i++) {
            y = (int)gga.charAt(i);
            x = x ^ y;
        }
        //转换成十六进制形式
        String check = Integer.toHexString(x).toUpperCase();

        return "$" + gga + "*" + check + "\r\n";
    }

    private static String getTime() {
        Date current = Date.from(Instant.now());
        SimpleDateFormat format = new SimpleDateFormat("HHmmss.ss");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(current);
    }

    /**
     * @param inputLati 0 - 90
     * @return
     */
    private static String getLati(Integer inputLati) {
        Random random = new Random();
        String lati = String.format("%02d", random.nextInt(inputLati));
        String latiMin = String.format("%02d", random.nextInt(60));
        String latiMilMin = String.format("%04d", random.nextInt(9999));
        return lati + latiMin + "." + latiMilMin;
    }

    private static String getLatiHemi() {
        Random random = new Random();
        String[] hemi = new String[] {"N", "S"};
        return hemi[random.nextInt(1)];
    }

    private static String getLongi(Integer inputLati) {
        Random random = new Random();
        String longi = String.format("%02d", random.nextInt(inputLati));
        String longiMin = String.format("%02d", random.nextInt(60));
        String longiMilMin = String.format("%04d", random.nextInt(9999));
        return longi + longiMin + "." + longiMilMin;
    }

    private static String getLongiHemi() {
        Random random = new Random();
        String[] longi = new String[] {"W", "E"};
        return longi[random.nextInt(1)];
    }

    public static void main1(String[] args) {
        System.out.println(getTime());
    }
}
