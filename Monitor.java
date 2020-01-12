import java.util.concurrent.LinkedBlockingQueue;
import java.net.InetAddress;
import java.net.DatagramSocket;
import java.net.SocketException;

public class Monitor {
    // The reserved nonce value;
    public static final long RESERVED_NONCE = -1;


    // The shared "channel" that all monitors use to indicate that a failure has
    // been detected
    static LinkedBlockingQueue<Monitor> clq = new LinkedBlockingQueue<Monitor>();

    // This variable is to be used to hold a string that unqiquelly identifies the Monitor
    String name;
    
    static long eNonce = RESERVED_NONCE;
    int threshHold;

    // This class method is used to set the epoch nonce, which is the value of the
    // epochNonce parameter. The return value is the channel the caller can
    // monitor to be notified that a failure has been detected.  If this method
    // is called more than once then a FailureDetectorException with the msg
    // "Monitor:  Already Initialized" is to be thrown.  The epoch value of -1 is
    // reserved and cannot be used. If the epochNonce is -1 then a
    // FailureDetectorException with the msg "Monitor: Invalid Epoch" is to be thrown.
    public static LinkedBlockingQueue<Monitor> initializeMonitor(long epochNonce)
	throws FailureDetectorException {
	System.out.println("initializeMonitor needs to be implemented");
	return clq;
    }

    // This class method is to cause all Monitors to stop monitoring their remote
    // node. 
    public static void stopMonitoringAll() {
     	System.out.println("stopMonitoringAll needs to be implemented");
    }
    
    // This constructor sets up a Monitor, but it does not start sending heartbeat
    // messages until the startMonitoring method is invoked.
    //  laddr - the address of the local interface to send UDP packets from and
    //          receive UDP packets on
    //  lport - the local port to use when sending heartbeat messages and receiving the
    //          acks
    //  raddr - the remote IP address to send the heartbeat messages to
    //  rport - the remote port number to sent the heartbeat messages to
    //  u_name - This sting is to be human readable value that the caller can
    //           use to provide a printable name for this Monitor.
    //
    // If the laddr:lport combination identify a port that cannot be opened
    // for the sending of UDP packets then a SocketException is to be thrown.
    // If any other type of error is detected that would prevent the operation of
    // the monitor then a FailureDetectionException, with a useful name (i.e.
    // the String parameter) is to be thrown.
    public Monitor(InetAddress laddr, int lport, InetAddress raddr, int port,
		   String u_name)
	throws FailureDetectorException, SocketException {
	name = u_name;
	System.out.println("The monitor constructor needs to be implemented");
    }

    // Start (or restart) monitoring the remote node using the threshold value.
    // If monitroing is currently in progress then the threshold value is
    // set to this new value. Note: this call does not block.
    public void startMonitoring(int threshold) throws FailureDetectorException {
	System.out.println("startMonitoring needs to be implemented");
    }

    // Stop monitoring the remote node. If the monitoring is currently in progress
    // then nothing is done. Any ACK messages received after this message are to be
    // ignored.
    public void stopMonitoring() {
	System.out.println("stopMonitoring needs to be implemented");
    }

    // Return the user supplied name for this Monitor
    public String getName() {
	return name;
    }


}
    
