package aqua.mal;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class CleanBadXMLInputStream extends FilterInputStream {
    protected CleanBadXMLInputStream(InputStream in) {
        super(in);
    }

    @Override
    public int read() throws IOException {
        int c = super.read();
        if (c < 32) {
            return ' ';
        } else {
            return c;
        }
    }

    @Override
    public int read(byte[] b) throws IOException {
        int read = super.read(b);
        for (int i = 0; i < read; ++i) {
            if (b[i] < 32) {
                b[i] = ' ';
            }
        }
        return read;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int read = super.read(b, off, len);
        for (int i = off, max = off + read; i < max; ++i) {
            if (b[i] < 32) {
                b[i] = ' ';
            }
        }
        return read;
    }
}
