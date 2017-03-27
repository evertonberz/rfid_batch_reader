/*
*
* ReaderInterface
*/

import java.io.*;
import java.util.*;

public class ReaderInterface {

  public ReaderInterface() {
  }

  public void logStatusEvent(String message) {
    System.out.println("(Time: " + System.currentTimeMillis() + ") ReaderStatusEvent: " + message);
  }

  public void logEpcEvent(String epc, int readCount, int totalCount) {
    System.out.println("(Time: " + System.currentTimeMillis() + ") ["+readCount+"/"+totalCount+"] EPC: " + epc);
  }

}

