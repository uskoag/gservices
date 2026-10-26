package uskoag.gservices.docs;

import com.google.api.services.drive.model.File;
import java.util.List;

/** Test helper: Drive's own copy of a file into a folder. Args: account fileId folderId name. */
public class DriveCopy {

    public static void main(String[] a) throws Exception {
        Auth.email = a[0];
        var f = Auth.driveUpload().files().copy(a[1], new File().setName(a[3]).setParents(List.of(a[2]))).setSupportsAllDrives(true).setFields("id").execute();
        System.out.println("COPY " + f.getId());
    }
}
