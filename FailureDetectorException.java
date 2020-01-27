public class FailureDetectorException extends Exception {
  public static final String IV_EPOCH = new String("Monitor: Invalid Epoch");
  public static final String RUNNING = new String("Already running");
  public static final String INITED = new String("Monitor: Already Initialized");
  public static final String NEEDS_INIT = new String("initializeMonitor() must be called");

  FailureDetectorException(String msg) {
    super(msg);
  }
}
