import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;

public class Monitor implements Runnable {
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

  private HashMap<Long, Long> rttLookUp;
  private DatagramSocket socket;
  private int numHeartbeats;
  private double lastRoundTripTime;
  private long lastAckReceivedInMs;
  private long lastAckSentInMs;
  private int lostMessageCount;
  private long sequenceNumber;
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
    this.socket = new DatagramSocket(lport, laddr);
    this.rttLookUp = new HashMap<>();
  }

  private void setSocketTimeout(long seqNum) {
    try {
      int rtt = this.estimateRTT(seqNum);
      System.out.println("New socket timeout: " + rtt);
      socket.setSoTimeout(rtt);
    } catch (Exception e) {
      System.out.println(e.getMessage());
    }
  }

  private void waitForAck() {
    while (this.isMonitoring) {
      System.out.println("Lost message count: " + this.lostMessageCount + ", threshold: " + this.threshHold);
      try {
        DatagramPacket response = this.createPacket(raddr, rport);
        if (this.numHeartbeats <= 1) {
          this.lastRoundTripTime = 3000;
          this.lastAckReceivedInMs = this.lastAckSentInMs + 3000;
          socket.setSoTimeout(3000);
        }
        socket.receive(response);
        ByteBuffer data = ByteBuffer.wrap(response.getData());

        if (response.getLength() == BUFFER_SIZE_IN_BYTES) {
          long epoch = data.getLong();
          long dataSeqNum = data.getLong();

          System.out.println("ACK received with epoch: " + epoch + ", sequence number: " + dataSeqNum);
          if (epoch == eNonce && this.rttLookUp.containsKey(dataSeqNum)) {
            this.lostMessageCount = 0;
          }
          this.setSocketTimeout(dataSeqNum);
          if (epoch != eNonce || !this.rttLookUp.containsKey(dataSeqNum)) {
            System.out.println("Bad message received!");
            continue;
          }
          this.lostMessageCount = 0;
          this.lastAckReceivedInMs = System.currentTimeMillis();
        }
      } catch (Exception exception) {
        System.out.println(exception.getMessage());
        if (exception instanceof SocketTimeoutException) {
          this.lostMessageCount++;
          break;
        }
      }
    }
  }

  @Override
  public void run() {
    while (this.isMonitoring && this.lostMessageCount <= (this.threshHold - 1)) {
      try {
        this.sendHeartbeat();
        this.waitForAck();
      } catch (Exception e) {
        System.out.println(e.getMessage());
      }
    }
    if (this.lostMessageCount >= this.threshHold) {
      clq.add(this);
      this.lostMessageCount = 0;
      this.numHeartbeats = 0;
      this.rttLookUp.clear();
    }
  }

  private int estimateRTT(long seqNum) {
    if (this.rttLookUp.containsKey(seqNum)) {
      long lastAckSentTime = this.rttLookUp.get(seqNum);

      if (lastAckSentTime > this.lastAckReceivedInMs) {
        this.lastAckReceivedInMs = 1000;
      }
      long currentRTT = (this.lastAckReceivedInMs - lastAckSentTime);
      int newRTT = Math.max(1, (int) Math.ceil((currentRTT + this.lastRoundTripTime) / 2));
      this.lastRoundTripTime = newRTT;
      return newRTT;
    }

    if (this.lastAckSentInMs > this.lastAckReceivedInMs) {
      this.lastAckReceivedInMs = 1000;
    }
    long currentRTT = (this.lastAckReceivedInMs - this.lastAckSentInMs);
    int newRTT = Math.max(1, (int) Math.ceil((currentRTT + this.lastRoundTripTime) / 2));
    this.lastRoundTripTime = newRTT;
    return newRTT;
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
    this.rttLookUp.put(this.sequenceNumber, this.lastAckSentInMs);
    try {
      System.out.println("Monitor sending heartbeat with epoch: " + eNonce + ", sequence number: " + sequenceNumber);
      socket.send(packet);
    } catch (Exception e) {
      System.out.println(e.getMessage());
    }
    this.sequenceNumber++;
  }

  // Start (or restart) monitoring the remote node using the threshold value.
  // If monitroing is currently in progress then the threshold value is
  // set to this new value. Note: this call does not block.
  public void startMonitoring(int threshold) throws FailureDetectorException {
    if (!this.isInitialized()) {
      throw new FailureDetectorException("Not initialized.");
    }
    this.isMonitoring = true;
    this.threshHold = threshold;
    Thread thread = new Thread(this);
    thread.setDaemon(true);
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
    return eNonce != RESERVED_NONCE;
  }
}
