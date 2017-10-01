package aqua.recommend;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

public class TmpFile implements AutoCloseable {
    public File targetFile, tmpFile;
    public OutputStream handle;

    public TmpFile(String targetFile, String tmpFile, OutputStream handle) {
        this.targetFile = new File(targetFile);
        this.tmpFile = new File(tmpFile);
        this.handle = handle;
    }

    @Override
    public void close() throws IOException {
        if (handle != null)
            discard();
    }

    public void discard() throws IOException {
        handle.close();
        tmpFile.delete();
    }

    public void commit() throws IOException {
        handle.close();
        tmpFile.renameTo(targetFile);
    }
}
