
import java.net.InetAddress;
import java.net.DatagramSocket;
import java.net.SocketException;

// This class sets up and controls the part of the system that responds to heartbeat messages

public class Responder {

    // This constructor creates a Responder object.
    //    port -- the port to listen for heartbeat messages
    //    laddr -- the IP address of the local interface to listen on. Keep in mind that a machine may
    //             have multiple interfaces and this is a way to select which interface to listen on.
    // If a  machine has multiple interfaces that require responders then multiple Responders can be used
    // to achieve this functonality. If the local ip address/port combination is invalid or in use then
    // a SocketException is to be thrown. Any other errors that result in a problem that would allow
    // things not to be monitored will result in a FailureDetectorException being thrown. 


    public Responder(int port, InetAddress laddr) throws FailureDetectorException, SocketException {
	System.out.println("Responder needs to be implemented");
    }

    // Prior to this method being invoked all heartbeat messages are ignored/discarded.
    // If startResponding() is invoked on an instance that is already responding then
    // a FailureDectectorException with the message "Already Running" is to be thrown.
    // If any other problem is detected then the FailureDectorException is thrown
    public void startResponding()throws FailureDetectorException {
	System.out.println("startResponding needs to be implemented");
    }

    // Once this method returns heartbeat messages will be discraded/ignored until a subsequent
    // startResponding call is made.

    public void stopResponding() {
	System.out.println("stopResponding needs to be implemented");
    }
}
    
