import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

// This class sets up and controls the part of the system that responds to heartbeat messages

public class Responder extends Thread {
  private static final int MESSAGE_SIZE_IN_BYTES = 16;
  private static final Map<InetAddress, Map<Integer, Boolean>> responders = new HashMap<InetAddress, Map<Integer, Boolean>>();

  private DatagramSocket socket;
  private int port;
  private InetAddress addr;
  private boolean isResponding;

  // This constructor creates a Responder object.
  // port -- the port to listen for heartbeat messages
  // laddr -- the IP address of the local interface to listen on. Keep in mind
  // that a machine may
  // have multiple interfaces and this is a way to select which interface to
  // listen on.
  // If a machine has multiple interfaces that require responders then multiple
  // Responders can be used
  // to achieve this functonality. If the local ip address/port combination is
  // invalid or in use then
  // a SocketException is to be thrown. Any other errors that result in a problem
  // that would allow
  // things not to be monitored will result in a FailureDetectorException being
  // thrown.

  public Responder(int port, InetAddress laddr) throws FailureDetectorException, SocketException {
    this.port = port;
    this.addr = laddr;

    if (!isPortAndAddressAvailable(port, laddr)) {
      throw new SocketException("Local IP Address and port is in use already.");
    }

    if (!isPortAvailable(port, laddr)) {
      throw new SocketException("Port is not available.");
    }

    this.socket = new DatagramSocket(this.port, this.addr);
    var map = new HashMap<Integer, Boolean>();
    map.put(port, false);
    responders.put(addr, map);
  }

  @Override
  public void run() {
    while (this.isResponding) {
      byte[] buf = new byte[MESSAGE_SIZE_IN_BYTES];
      DatagramPacket packet = new DatagramPacket(buf, buf.length);
      try {
        this.socket.receive(packet);
        ByteBuffer response = ByteBuffer.wrap(packet.getData());
        if (response.capacity() == MESSAGE_SIZE_IN_BYTES) {
          long epochNonce = response.getLong();
          long seqNumber = response.getLong();

          System.out.println("Heartbeat received with epoch: " + epochNonce + ", sequence number: " + seqNumber);

          InetAddress responseAddress = packet.getAddress();
          int responsePort = packet.getPort();

          this.sendAck(epochNonce, seqNumber, responsePort, responseAddress);
        }
      } catch (Exception e) {
        System.out.println(e.getMessage());
      }
    }
  }

  boolean isPortAndAddressAvailable(int port, InetAddress address) {
    if (responders.containsKey(address)) {
      var map = responders.get(address);
      return !map.containsKey(port);
    }
    return true;
  }

  boolean isResponding(int port, InetAddress address) {
    if (responders.containsKey(address)) {
      var map = responders.get(address);
      return map.get(port) && this.isResponding;
    }
    return false;
  }

  public static boolean isPortAvailable(int port, InetAddress addr) {
    try (var ds = new DatagramSocket(port, addr)) {
      ds.close();
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  private void sendAck(long epochNonce, long seqNumber, int port, InetAddress address) {
    final ByteBuffer buffer = ByteBuffer.allocate(MESSAGE_SIZE_IN_BYTES);
    buffer.putLong(epochNonce);
    buffer.putLong(seqNumber);
    buffer.rewind();
    DatagramPacket ack = new DatagramPacket(buffer.array(), buffer.limit(), address, port);

    try {
      System.out.println("Responder sending ACK with epoch " + epochNonce + ", sequence number: " + seqNumber);
      socket.send(ack);
    } catch (Exception e) {
    }
  }

  // Prior to this method being invoked all heartbeat messages are
  // ignored/discarded.
  // If startResponding() is invoked on an instance that is already responding
  // then
  // a FailureDectectorException with the message "Already Running" is to be
  // thrown.
  // If any other problem is detected then the FailureDectorException is thrown
  public void startResponding() throws FailureDetectorException, SocketException {
    if (isResponding(this.port, this.addr)) {
      throw new FailureDetectorException("Already running.");
    } else {
      responders.get(this.addr).replace(this.port, true);
    }
    this.isResponding = true;
    this.setDaemon(true);
    this.start();
  }

  // Once this method returns heartbeat messages will be discraded/ignored until a
  // subsequent
  // startResponding call is made.
  public void stopResponding() {
    this.isResponding = false;
  }
}
