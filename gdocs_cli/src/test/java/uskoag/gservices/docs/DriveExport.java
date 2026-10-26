package uskoag.gservices.docs;

import java.io.FileOutputStream;

/** Test helper: Drive's export of a Google Doc. Args: account fileId mime outFile. */
public class DriveExport {

    public static void main(String[] a) throws Exception {
        Auth.email = a[0];
        try (var out = new FileOutputStream(a[3])) { Auth.drive().files().export(a[1], a[2]).executeMediaAndDownloadTo(out); }
        System.out.println("EXPORTED " + a[3]);
    }
}
