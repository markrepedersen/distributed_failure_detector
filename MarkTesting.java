import java.net.InetAddress;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class MarkTesting {
  public static void main(String[] args) throws Exception {
    // Initialize and Create Monitor to send heartbeat messages
    // Observe that we are going to monitor ourselves.
    LinkedBlockingQueue<Monitor> fdQueue = Monitor.initializeMonitor(80085);
    Monitor mon = new Monitor(InetAddress.getByName("127.0.0.1"), 0, InetAddress.getByName("thetis.students.cs.ubc.ca"),
        35321, "TestMonitor of localhost:" + 12345);
    // startMonitoring with a threshold of 6
    System.out.println("Starting monitoring.");
    mon.startMonitoring(10);

    // wait a maximum of 10 seconds for a failure
    fdQueue.poll(500, TimeUnit.SECONDS);
  }
}
