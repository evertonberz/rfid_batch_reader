import java.io.*;
import java.net.*;
import java.util.*;

import java.sql.*;

public class TMReaderAdapter {

  private ReaderInterface queue;
  private Thread thread;
  private PrintWriter readerOut = null;
  private BufferedReader readerIn = null;
  private Socket socket = null;
  public boolean threadExiting = false;
  private String keywordValue;
  private String debugfile= null;
  private String csvfile=null;
  private int csv = 0;
  private PrintWriter filepw = null;
  private PrintWriter filepwc = null;

  private String ipaddr = "10.0.0.101";
  public int power = 0;  // 0 means reader configuration
  public int timeLimit;
  private String whereClause = "";
  private int reset = 1;

  private boolean statEnabled;

  private int jdbc = 0;
  private String testName = "test";
  private int cleanResults = 0;

  public int readCount = 0;
  public int totalCount = 0;


  private int port = 8080;
  private int repeat = 250;
  private int debug = 0;
  private String triggerTime = "";
  private String cursorName = "c1";

  private final static int DEBUG_CMD = 0x0001;
  private final static int DEBUG_RSP = 0x0002;
  private final static int DEBUG_DATA = 0x0004;
  private final static int DEBUG_KEYWORDS = 0x0008;
  private final static int DEBUG_ALIVE = 0x0010;
  private final static int DEBUG_EXCEPTIONS = 0x0020;

  private final static String[] keywords = {"ipaddr", "port", "repeat", "protocol",
                        "antenna", "reset", "debugflags", "readerepc",
                        "readerepcUHF1", "readerepcUHF2",
                        "readerepcHF1", "readerepcHF2", "debugfile",
                        "trigger", "power", "csv", "jdbc", "testname",
                        "cleanresults"};

  //bd stuff
  public Connection c = null;
  private String bdName = "rfidmercury";
  private String bdUser = "rfidmercury";
  private String bdPass = "";

  /* Constructor */
  public TMReaderAdapter(String initString, ReaderInterface queue, int timeLimit) throws IOException {
    String command;
    this.queue = queue;
    this.timeLimit = timeLimit;
    this.statEnabled = (timeLimit > 0);
    parseInitString(initString);            // decode all the parameters

    if (debugfile != null) {
      try {
        filepw = new PrintWriter(new FileWriter(debugfile, true));    // append to existng file
      } catch (Exception e) {
        if ((debug & DEBUG_EXCEPTIONS) != 0)
          System.out.println("Exception opening log file " + debugfile + ": " + e.getMessage());
        throw new IOException();
      }
    }

    if (csv == 1) {
      try {
        csvfile = testName+".csv";
        boolean novo = false;
        if (this.cleanResults == 1) {
          File f = new File(csvfile);
          if (f.exists()) {
            f.delete();
            novo = true;
          }
        }
        filepwc = new PrintWriter(new FileWriter(csvfile, true));    // append to existng file
        if (novo)
          writecsv("time;power;frequency;id");
      } catch (Exception e) {
        System.out.println("Exception opening csv file " + csvfile + ": " + e.getMessage());
        throw new IOException();
      }
    }

    if (jdbc == 1) {
      try {
        Class.forName("org.postgresql.Driver");
      } catch (ClassNotFoundException cnfe) {
        System.err.println("Couldn't find driver class:");
        cnfe.printStackTrace();
      }
      try {
        c = DriverManager.getConnection("jdbc:postgresql://localhost/"+this.bdName,
                                        this.bdUser, this.bdPass);
      } catch (SQLException se) {
        System.out.println("Couldn't connect: print out a stack trace and exit.");
        se.printStackTrace();
        System.exit(1);
      }

      //delete all test hits
      if (this.cleanResults == 1) {
          try {
            Statement stmt = this.c.createStatement();
            stmt.executeUpdate("DELETE FROM hit WHERE testname = '"+this.testName+"'");
          } catch (SQLException se) {
            System.out.println("We got an exception while executing our query:" +
               "that probably means our SQL is invalid");
               se.printStackTrace();
            System.exit(1);
          }
      }
    }

    try {
      socket = new Socket(ipaddr, port);
      readerOut = new PrintWriter(socket.getOutputStream(), true);
      readerIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    } catch (Exception e) {
      if ((debug & DEBUG_EXCEPTIONS) != 0)
        log("Unable to connect to reader: " + e.getMessage());
      throw new IOException();
    }

    String parametersMsg = "Parameters: "+initString+" timeLimit="+timeLimit;
    log(parametersMsg);
    queue.logStatusEvent(parametersMsg);


    /*  Start the reader */

    if (reset == 1) {
      tellReader("RESET;", false);
    }
    if (power > 0) {
      //tellReader("SELECT tx_power FROM saved_settings;", true);
      //
      command = "UPDATE saved_settings SET tx_power='" + power +  "';";
      log(command);
      tellReader(command, false);
      //
      //tellReader("SELECT tx_power FROM saved_settings;", true);
      this.cursorName = "c"+power;
    }

    command = "UPDATE params SET gen2initq='0';"; //default: 3
    log(command);
    tellReader(command, false);

    command = "UPDATE params SET gen2initqsmall='0';"; //default: 3
    log(command);
    tellReader(command, false);

    command = "UPDATE params SET gen2minq='2';"; //default: 2
    log(command);
    tellReader(command, false);


    //tellReader("SELECT gen2initq, gen2initqsmall, gen2minq FROM params;", true);

    command = "DECLARE "+this.cursorName+" CURSOR FOR "+
              "SELECT protocol_id, antenna_id, read_count, timestamp, frequency, id "+
              "FROM tag_id " +
              whereClause +
              " SET time_out=0;";  
    log(command);
    tellReader(command, true);  //todo:  handle error if cursor already defined

    //repeat command
    if (repeat >= 0) {
      command = "SET auto "+this.cursorName+" = ON, repeat = " + repeat + ";";
      log(command);
      tellReader(command, false);
    }

    //trigger commands
    if (!triggerTime.isEmpty()) {
      command = "SET auto_time "+this.cursorName+" = " + "'" + triggerTime + "';";
      log(command);
      tellReader(command, false);
    }

    /* Create a thread to read on this socket */

    thread = new Thread(new ReaderThread(this));
    thread.setName("Reader Adapter thread");
    thread.setDaemon(true);
    thread.start();

    return;
  }

  /* Methods */

  public void shutdown() {
    thread = null;  // Tell reader thread to exit
    try {
      //if 0 hits, tell csv about that
      if (this.readCount == 0 && this.statEnabled) {
        writecsv(this.timeLimit+";"+this.power+";0;0");
        writedb(this.testName, this.timeLimit, this.power, 0, 0, 0, "", "");
      }

      tellReader("SET auto "+this.cursorName+" = OFF;", false);
      tellReader("CLOSE "+this.cursorName+";", false);
      readerOut.close();
      readerIn.close();
      socket.close();
    } catch (Exception e) {
      log("Exception in TMReaderAdapter.shutdown(): " + e.getMessage());
    } finally  {
      socket = null;
    }
    if (filepw != null)
      filepw.close();
    if (filepwc != null)
      filepwc.close();
  }

  public void tellReader (String cmd, boolean getResponse) throws IOException {
    String data;
    if ((debug & DEBUG_CMD) != 0)
      log("Tell reader: " + cmd);
    try {
      readerOut.println(cmd); // Send command to reader
    } catch (Exception e) {
      if ((debug & DEBUG_EXCEPTIONS) != 0)
        log("Exception sending command to reader: " + e.getMessage());
      throw new IOException();
    }
    if (!getResponse)
      return;
    while (true) { // Loop until success or error.  Ignore data
      try {
        data = readerIn.readLine();
      } catch (Exception e) {
        if ((debug & DEBUG_EXCEPTIONS) != 0)
          log("Exception reading command from  reader: " + e.getMessage());
        throw new IOException();
      }
      if (data.length() == 0)
        return; // success
      if ((debug & DEBUG_RSP) != 0)
        log("Command error from reader: " + data);
      if (data.length() >= 5) {
        if (data.startsWith("Error")) {
          if ((debug & DEBUG_EXCEPTIONS) != 0)
            log("Command error from reader: " + data);
          throw new IOException();
        }
      }
    }
  }

  private void parseInitString(String initString) {
    StringTokenizer st = new StringTokenizer(initString, " ,;\t\n\r\f");
    while (st.hasMoreTokens()) {
      switch (getKeywordValue(st.nextToken(), keywords)) {
        case 0:        // ipaddr
          ipaddr = keywordValue;
          break;

        case 1:        // port
          port = Integer.parseInt(keywordValue);
          break;

        case 2:        // repeat
          repeat = Integer.parseInt(keywordValue);
          break;

        case 3:        // protocol
          if (whereClause == "")
            whereClause = "WHERE protocol_id='" + keywordValue +"'";
          else
            whereClause = whereClause + " AND protocol_id=" + keywordValue;
          break;

        case 4:        // antenna
          if (whereClause == "")
            whereClause = "WHERE antenna_id=" + keywordValue;
          else
            whereClause = whereClause + " AND antenna_id=" + keywordValue;
          break;

        case 5:        // reset
          reset = Integer.parseInt(keywordValue);
          break;

        case 6:        // debugflags
          if ((keywordValue.length() >= 2)  &&
             (keywordValue.charAt(0) == '0' && keywordValue.charAt(1) == 'x')) {
              debug = Integer.parseInt(keywordValue.substring(2), 16); // debug is only variable supported in hex
            } else {
              debug = Integer.parseInt(keywordValue);
            }
          break;


        case 12:        // debugfile
          debugfile = keywordValue;
          break;

        case 13:          //trigger
          triggerTime = keywordValue;
          break;

        case 14:
          power = Integer.parseInt(keywordValue);
         break;

        case 15:
          csv = Integer.parseInt(keywordValue);
         break;

        case 16:
          jdbc = Integer.parseInt(keywordValue);
          break;

        case 17:
          testName = keywordValue;
          break;

        case 18:
            cleanResults = Integer.parseInt(keywordValue);
            break;

        default:    // Anything else is ignored
      }
    }
  }

  private int getKeywordValue(String token, String[] table) {
    int i, p;
    if ((p = token.indexOf('=')) < 0)
      return(-1); // No '='
    String keyword = token.substring(0, p);
    for (i = 0; i < table.length; i++) {
      if (keyword.equalsIgnoreCase(table[i])) {
        keywordValue = token.substring(p + 1);
        if ((debug & DEBUG_KEYWORDS) != 0)
          log(keyword + "=" + keywordValue);
        return (i);
      }
    }
    return(-1); // Keyword not in table
  }

  private void log(String text) {
    if (filepw != null) {
      filepw.println(text);
      filepw.flush();
    } else {
      System.out.println(text);
    }
  }

  private void writecsv(String text) {
    if (filepwc != null) {
      filepwc.println(text);
      filepwc.flush();
    }
  }

  private void writedb(String testName, int timeLimit, int power, int frequency, int protocol, int antenna, String timestamp, String epc) {
    if (this.c != null) {
      try {
        Statement stmt = this.c.createStatement();
        if (epc.length() == 0) {
          epc = "NULL";
        } else {
          epc = "'"+epc+"'";
        }
        stmt.executeUpdate("INSERT INTO hit (testname, timelimit, power, frequency, protocol, antenna, timestamp, epc, current_datetime) VALUES "+
            "('"+testName+"', "+timeLimit+", "+power+", "+frequency+", "+protocol+", "+antenna+", '"+timestamp+"', "+epc+", now() )");
      } catch (SQLException se) {
        System.out.println("We got an exception while executing our query:" +
           "that probably means our SQL is invalid");
           se.printStackTrace();
        System.exit(1);
      }
    }
  }


  public void doStatControl() {

    //if 0 hits, tell csv and db about that
    if (this.readCount == 0 && this.statEnabled) {
      writecsv(this.timeLimit+";"+this.power+";0;0");
      writedb(this.testName, this.timeLimit, this.power, 0, 0, 0, "", "");
    }
    
    this.statEnabled = !this.statEnabled;    
    if (this.statEnabled) {
      queue.logStatusEvent("Statistics enabled!");
      log("Statistics enabled!");
    } else {
      queue.logStatusEvent("Statistics disabled!");
      log("Statistics disabled!");
    }
  }


  class ReaderThread implements Runnable {

    /*  Data */
    private TMReaderAdapter reader;


    /* Constructor */
    ReaderThread(TMReaderAdapter reader) {
      this.reader = reader;
    }

    /*  Methods */
    public void run() {
      String data;
      String colEpc = "";
      int colProtocol = 0;
      int colAntenna = 0;
      int colReadCount = 0;
      String colSqlTimestamp = "";
      int colFrequency = 0;

      Thread thisThread = Thread.currentThread();
      while (thisThread == thread) {
        try {
          data = readerIn.readLine(); // wait for next line from reader
        } catch (Exception e) {
          if (reader.thread != null) { // Only log an event if we are not shutting down intentionally
            queue.logStatusEvent("Exception in reader thread: " + e.getMessage());
          }
          break;
        }

        reader.totalCount++;

        if (data.length() == 0) {
          if ((debug & DEBUG_ALIVE) != 0)
            log("["+reader.totalCount+"] Raw reader data (0)");
          continue;
        }

        if ((debug & DEBUG_DATA) != 0) {
          log("(Time: "+System.currentTimeMillis() + ") ["+reader.readCount+"/"+reader.totalCount+"] Raw reader data (" + data.length() + "): '" + data + "'");
        }

        if (data.length() >= 5) {
          if (data.startsWith("Error")) {
            queue.logStatusEvent("Error from reader: " + data);
            break;
          } else if (data.length() >= 15) {
            //sql result parser
            String delims = "[|]";
            String[] tokens = data.split(delims);
            for (int i = 0; i < tokens.length; i++) {
              switch (i) {
                case 0: // protocol_id
                  colProtocol = Integer.parseInt(tokens[i]);
                  break;
                case 1: // antenna_id
                  colAntenna = Integer.parseInt(tokens[i]);
                  break;
                case 2: // read_count
                  colReadCount = Integer.parseInt(tokens[i]);
                  break;
                case 3: // timestamp
                  colSqlTimestamp = tokens[i];
                  break;
                case 4: // frequency
                  colFrequency = Integer.parseInt(tokens[i]);
                  break;
                case 5: // id
                  colEpc = tokens[i];
                  break;
                default:
              }
            }

         for (int i = 1; i <= colReadCount; i++) { 
              if (reader.statEnabled) {
                writecsv(reader.timeLimit+";"+reader.power+";"+colFrequency+";"+colEpc);
                writedb(reader.testName, reader.timeLimit, reader.power, colFrequency, colProtocol, colAntenna, colSqlTimestamp, colEpc);
              }
              reader.readCount++;
            }

            queue.logEpcEvent(colEpc, reader.readCount, reader.totalCount);
            continue;
          }

        }
      }  //end while
      reader.threadExiting = true;
    } //end run

  } //end ReaderThread class
}
