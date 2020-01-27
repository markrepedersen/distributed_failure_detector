import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

public class Monitor extends Runnable {
  // The reserved nonce value;
  public static final long RESERVED_NONCE = -1;

  private static final int BUFFER_SIZE_IN_BYTES = 16;
  private static boolean isMonitoringAll = true;

  // The shared "channel" that all monitors use to indicate that a failure has
  // been detected
  static LinkedBlockingQueue<Monitor> clq = new LinkedBlockingQueue<Monitor>();

  // This variable is to be used to hold a string that unqiquelly identifies the
  // Monitor
  String name;

  private DatagramSocket socket;
  private int currentRTT;
  private List<Integer> roundTripTimes;
  private int numHeartbeats;
  private int lastAckReceivedInMs;
  private int lastAckSentInMs;
  private int lostMessageCount;
  private int sequenceNumber;
  private int lport;
  private InetAddress laddr;
  private int rport;
  private InetAddress raddr;
  private boolean isMonitoring;

  static long eNonce = RESERVED_NONCE;
  int threshHold;

  // This class method is used to set the epoch nonce, which is the value of the
  // epochNonce parameter. The return value is the channel the caller can
  // monitor to be notified that a failure has been detected. If this method
  // is called more than once then a FailureDetectorException with the msg
  // "Monitor:  Already Initialized" is to be thrown. The epoch value of -1 is
  // reserved and cannot be used. If the epochNonce is -1 then a
  // FailureDetectorException with the msg "Monitor: Invalid Epoch" is to be
  // thrown.
  public static LinkedBlockingQueue<Monitor> initializeMonitor(long epochNonce) throws FailureDetectorException {
    if (epochNonce == RESERVED_NONCE) {
      throw new FailureDetectorException("Monitor: Invalid Epoch");
    }
    eNonce = epochNonce;
    return clq;
  }

  // This class method is to cause all Monitors to stop monitoring their remote
  // node.
  public static void stopMonitoringAll() {
    isMonitoringAll = false;
  }

  // This constructor sets up a Monitor, but it does not start sending heartbeat
  // messages until the startMonitoring method is invoked.
  // laddr - the address of the local interface to send UDP packets from and
  // receive UDP packets on
  // lport - the local port to use when sending heartbeat messages and receiving
  // the
  // acks
  // raddr - the remote IP address to send the heartbeat messages to
  // rport - the remote port number to sent the heartbeat messages to
  // u_name - This sting is to be human readable value that the caller can
  // use to provide a printable name for this Monitor.
  //
  // If the laddr:lport combination identify a port that cannot be opened
  // for the sending of UDP packets then a SocketException is to be thrown.
  // If any other type of error is detected that would prevent the operation of
  // the monitor then a FailureDetectionException, with a useful name (i.e.
  // the String parameter) is to be thrown.
  public Monitor(InetAddress laddr, int lport, InetAddress raddr, int port, String u_name)
      throws FailureDetectorException, SocketException {
    this.name = u_name;
    this.laddr = laddr;
    this.lport = lport;
    this.raddr = raddr;
    this.rport = port;
    this.isMonitoring = false;
    this.sequenceNumber = 0;
    this.lostMessageCount = 0;
    this.numHeartbeats = 0;
    this.roundTripTimes = new ArrayList<>();

    if (!this.isPortAvailable(lport, laddr)) {
      throw new SocketException("Port on this interface is already in use.");
    }
  }

  private void setSocketTimeout() {
    int rtt = this.estimateRTT();
    socket.setSoTimeout(rtt);
  }

  private void waitForAck() {
    while (this.isMonitoring) {
      try {
        DatagramPacket response = this.createPacket(raddr, rport);
        this.setSocketTimeout();
        socket.receive(response);

        ByteBuffer data = ByteBuffer.wrap(response.getData());

        if (data.capacity() == BUFFER_SIZE_IN_BYTES) {
          long epoch = data.getLong();
          long sequenceNumber = data.getLong();

          if (epoch != eNonce || sequenceNumber != this.sequenceNumber) {
            continue;
          }
          this.lastAckReceivedInMs = System.currentTimeMillis();
        }
      } catch (SocketTimeoutException exception) {
        this.lostMessageCount++;
        break;
      }
    }
  }

  @Override
  public void run() {
    if (!this.isInitialized()) {
      throw new FailureDetectorException("Not initialized.");
    }
    while (this.isMonitoring && this.lostMessageCount <= (this.threshHold - 1)) {
      this.socket = new DatagramSocket(lport, laddr);
      this.sendHeartbeat();
      this.waitForAck();
    }
  }

  private double estimateRTT() {
    if (this.numHeartbeats <= 1) {
      return 3000;
    }
    this.roundTripTimes.add(this.lastAckReceivedInMs - this.lastAckSentInMs);
    var stats = roundTripTimes.stream().mapToInt(Integer::intValue).summaryStatistics();
    return stats.getAverage();
  }

  private DatagramPacket createPacket(InetAddress address, int port) {
    ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE_IN_BYTES);
    return new DatagramPacket(buffer.array(), buffer.capacity(), address, port);
  }

  private DatagramPacket createPacket(InetAddress address, int port, ByteBuffer buffer) {
    return new DatagramPacket(buffer.array(), buffer.capacity(), address, port);
  }

  private void sendHeartbeat() {
    ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE_IN_BYTES);
    buffer.putLong(eNonce);
    buffer.putLong(this.sequenceNumber);

    DatagramPacket packet = this.createPacket(raddr, rport, buffer);

    this.numHeartbeats++;
    this.lastAckSentInMs = System.currentTimeMillis();
    socket.send(packet);
    this.sequenceNumber++;
  }

  // Start (or restart) monitoring the remote node using the threshold value.
  // If monitroing is currently in progress then the threshold value is
  // set to this new value. Note: this call does not block.
  public void startMonitoring(int threshold) throws FailureDetectorException {
    this.isMonitoring = true;
    this.threshHold = threshold;
    Thread thread = new Thread(this);
    thread.start();
  }

  // Stop monitoring the remote node. If the monitoring is currently in progress
  // then nothing is done. Any ACK messages received after this message are to be
  // ignored.
  public void stopMonitoring() {
    this.isMonitoring = false;
  }

  // Return the user supplied name for this Monitor
  public String getName() {
    return name;
  }

  private boolean isInitialized() {
    return eNonce == RESERVED_NONCE;
  }
}
