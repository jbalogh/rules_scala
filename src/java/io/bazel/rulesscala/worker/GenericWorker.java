package io.bazel.rulesscala.worker;

import com.google.devtools.build.lib.worker.WorkerProtocol.WorkRequest;
import com.google.devtools.build.lib.worker.WorkerProtocol.WorkResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map.Entry;
import java.util.Map;
import java.util.TreeMap;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.InputStream;
import java.io.OutputStream;


public class GenericWorker {
    
public class TeeInputStream extends java.io.FilterInputStream {

    /**
     * The output stream that will receive a copy of all bytes read from the
     * proxied input stream.
     */
    private final OutputStream branch;

    /**
     * Flag for closing also the associated output stream when this
     * stream is closed.
     */
    private final boolean closeBranch;

    /**
     * Creates a TeeInputStream that proxies the given {@link InputStream}
     * and copies all read bytes to the given {@link OutputStream}. The given
     * output stream will not be closed when this stream gets closed.
     *
     * @param input input stream to be proxied
     * @param branch output stream that will receive a copy of all bytes read
     */
    public TeeInputStream(final InputStream input, final OutputStream branch) {
        this(input, branch, false);
    }

    /**
     * Creates a TeeInputStream that proxies the given {@link InputStream}
     * and copies all read bytes to the given {@link OutputStream}. The given
     * output stream will be closed when this stream gets closed if the
     * closeBranch parameter is {@code true}.
     *
     * @param input input stream to be proxied
     * @param branch output stream that will receive a copy of all bytes read
     * @param closeBranch flag for closing also the output stream when this
     *                    stream is closed
     */
    public TeeInputStream(
            final InputStream input, final OutputStream branch, final boolean closeBranch) {
        super(input);
        this.branch = branch;
        this.closeBranch = closeBranch;
    }

    /**
     * Closes the proxied input stream and, if so configured, the associated
     * output stream. An exception thrown from one stream will not prevent
     * closing of the other stream.
     *
     * @throws IOException if either of the streams could not be closed
     */
    @Override
    public void close() throws IOException {
        try {
            super.close();
        } finally {
            if (closeBranch) {
                branch.close();
            }
        }
    }

    /**
     * Reads a single byte from the proxied input stream and writes it to
     * the associated output stream.
     *
     * @return next byte from the stream, or -1 if the stream has ended
     * @throws IOException if the stream could not be read (or written) 
     */
    @Override
    public int read() throws IOException {
        final int ch = super.read();
        if (ch != -1) {
            branch.write(ch);
        }
        return ch;
    }

    /**
     * Reads bytes from the proxied input stream and writes the read bytes
     * to the associated output stream.
     *
     * @param bts byte buffer
     * @param st start offset within the buffer
     * @param end maximum number of bytes to read
     * @return number of bytes read, or -1 if the stream has ended
     * @throws IOException if the stream could not be read (or written) 
     */
    @Override
    public int read(final byte[] bts, final int st, final int end) throws IOException {
        final int n = super.read(bts, st, end);
        if (n != -1) {
            branch.write(bts, st, n);
        }
        return n;
    }

    /**
     * Reads bytes from the proxied input stream and writes the read bytes
     * to the associated output stream.
     *
     * @param bts byte buffer
     * @return number of bytes read, or -1 if the stream has ended
     * @throws IOException if the stream could not be read (or written) 
     */
    @Override
    public int read(final byte[] bts) throws IOException {
        final int n = super.read(bts);
        if (n != -1) {
            branch.write(bts, 0, n);
        }
        return n;
    }

}

  final protected Processor processor;

  public GenericWorker(Processor p) {
    processor = p;
  }

  protected void setupOutput(PrintStream ps) {
    System.setOut(ps);
    System.setErr(ps);
  }

  // Mostly lifted from bazel
  private void runPersistentWorker() throws IOException {
    PrintStream originalStdOut = System.out;
    PrintStream originalStdErr = System.err;
      
      
    
    

    while (true) {
      try {
        
        
        
        WorkRequest request = WorkRequest.parseDelimitedFrom(new TeeInputStream(System.in, new java.io.FileOutputStream(new java.io.File("/tmp/waaaaat"))));
        if (request == null) {
          break;
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int exitCode = 0;

        try (PrintStream ps = new PrintStream(baos)) {
          setupOutput(ps);

          try {
            processor.processRequest(request.getArgumentsList());
          } catch (Exception e) {
            e.printStackTrace();
            exitCode = 1;
          }
        } finally {
          System.setOut(originalStdOut);
          System.setErr(originalStdErr);
        }

        WorkResponse.newBuilder()
            .setOutput(baos.toString())
            .setExitCode(exitCode)
            .build()
            .writeDelimitedTo(System.out);
        System.out.flush();
      } finally {
        System.gc();
      }
    }
  }

  public static <T> String[] appendToString(String[] init, List<T> rest) {
    String[] tmp = new String[init.length + rest.size()];
    System.arraycopy(init, 0, tmp, 0, init.length);
    int baseIdx = init.length;
    for(T t : rest) {
      tmp[baseIdx] = t.toString();
      baseIdx += 1;
    }
    return tmp;
  }
  public static String[] merge(String[]... arrays) {
    int totalLength = 0;
    for(String[] arr:arrays){
      totalLength += arr.length;
    }

    String[] result = new String[totalLength];
    int offset = 0;
    for(String[] arr:arrays){
      System.arraycopy(arr, 0, result, offset, arr.length);
      offset += arr.length;
    }
    return result;
  }

  private boolean contains(String[] args, String s) {
    for (String str : args) {
      if (str.equals(s)) return true;
    }
    return false;
  }


  private static List<String> normalize(List<String> args) throws IOException {
    if (args.size() == 1 && args.get(0).startsWith("@")) {
      return Files.readAllLines(Paths.get(args.get(0).substring(1)), UTF_8);
    }
    else {
      return args;
    }
  }

  /**
   * This is expected to be called by a main method
   */
  public void run(String[] argArray) throws Exception {
    if (contains(argArray, "--persistent_worker")) {
      runPersistentWorker();
    }
    else {
      List<String> args = Arrays.asList(argArray);
      processor.processRequest(normalize(args));
    }
  }
}
