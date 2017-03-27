import java.io.*;
import java.net.*;
import java.util.*;

public class ReaderMain {

  public static void main(String argv[]) throws Exception {
    TMReaderAdapter reader;
    ReaderInterface queue;

    if (argv.length < 2) {                        // Test for correct # of args
      System.out.println("Usage: ReaderMain initString timeLimit");
      System.out.println("Example: ReaderMain repeat=0,debugfile=log.txt 10");
      System.out.println("If the timeLimit is equal to 0, you can control the csv/db writing by pressing 's'.");
      return;
    }
    queue = new ReaderInterface();

    int timeLimit = Integer.parseInt(argv[1]);
    //get current time to use in time limit test
    long startTime = System.currentTimeMillis();

    reader = new TMReaderAdapter(argv[0], queue, timeLimit);

    if (timeLimit > 0) {
      while (true) {
        float elapsedTimeSec = (System.currentTimeMillis()-startTime)/1000F;
        if (elapsedTimeSec >= timeLimit) {
          break;
        }
      }
    } else {
      while (true) {
        byte[] cmdBuffer = new byte[40];
        int len = System.in.read(cmdBuffer, 0, cmdBuffer.length);
        if (len > 0) {
          if (cmdBuffer[0] == 'q' || cmdBuffer[0] == 'Q') {
            break;
          }
          if (cmdBuffer[0] == 's' || cmdBuffer[0] == 'S') {
            reader.doStatControl();
          }
        }
      }
    }

    reader.shutdown();

    return;
  }
}
