package uskoag.gservices;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class OAuthToken {
    
    public final String appName;
    private final String appKey, encryptedAppKey;
    private Path allCredsDir, activeCredDir;
    private final List<String> scopes;
    private int port = 8888;
    private String accessType = "offline"; 
    private String user = "user";
    private String credentialsFolderNamePattern = ".+@.+";
        
    public OAuthToken(String appName, String appKey, String scope1st, String ... scopesStrRest) {
        this.appName = appName;
        this.appKey = appKey;
        encryptedAppKey = ((Supplier<String>)()->{
            try {
                var algo = MessageDigest.getInstance("MD5");// "SHA-256";
                return HexFormat.of().formatHex(algo.digest(appKey.getBytes(UTF_8)));
            } catch (Exception e) { return appKey; }
        }).get();
        
        var scopesSet = new LinkedHashSet<String>(); // for dedup
        scopesSet.add(scope1st);
        if(scopesStrRest!=null) scopesSet.addAll(List.of(scopesStrRest));
        scopes = new ArrayList<>(scopesSet);
        
        allCredsDirFromHome("uskoag","gdrive_gdocs_auth");
    }
    
    public static final OAuthToken oauthToken(String appName, String appKey, String scope1st, String ... scopesStrRest){
        return new OAuthToken(appName, appKey, scope1st, scopesStrRest);
    }
    
    public OAuthToken allCredsDir(Path allCredsDir){
        this.allCredsDir = allCredsDir;
        return this;
    }
    
    public OAuthToken allCredsDirFromHome(String ... pathRelativeFromUserHome){
        allCredsDir = Paths.get(System.getProperty("user.home"),pathRelativeFromUserHome);
        return this;
    }
 
    public OAuthToken credential(String name){
        activeCredDir = findCredential(e->e.equals(name)); 
        return this;
    }
    
    public OAuthToken defaultCredential(){
        activeCredDir = findCredential(e->e.matches(credentialsFolderNamePattern)); 
        return this;
    }
    
    public Path activeCredDir(){
        if(activeCredDir==null)defaultCredential();
        return activeCredDir;
    }
    
    private Path findCredential(Predicate<String> nm){
        try {
            return Files.list(allCredsDir)
                    .filter(Files::isDirectory)
                    .filter(e->nm.test(e.getFileName().toString()))
                    .findFirst()
                    .orElseThrow(()->new IllegalStateException("No matching credential found"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
    
    private EncryptedFileDataStoreFactory tokensDataStore() throws IOException {
        var tokensDir = activeCredDir.resolve("tokens_"+encryptedAppKey).toFile();
        // Encrypt tokens with appKey to protect against infostealer malware
        return new EncryptedFileDataStoreFactory(tokensDir, appKey);
    }
    
    private GoogleClientSecrets clientSecrets() throws IOException {
        var f = activeCredDir.resolve("credentials.json");
        var inr = new InputStreamReader(Files.newInputStream(f));
        return GoogleClientSecrets.load(GsonFactory.getDefaultInstance(), inr);
    }

    public OAuthToken port(int port){
        this.port = port;
        return this;
    }

    public OAuthToken accessType(String accessType){
        this.accessType = accessType;
        return this;
    }
            
    public OAuthToken user(String user){
        this.user = user;
        return this;
    }
            
    public Credential credentials(final NetHttpTransport transport) throws IOException {
        var flow = new GoogleAuthorizationCodeFlow.Builder(
            transport, GsonFactory.getDefaultInstance(), clientSecrets(), scopes
        )
        .setDataStoreFactory(tokensDataStore())
        .setAccessType(accessType)
        .build();
        var receiver = new LocalServerReceiver.Builder().setPort(port).build();

        return new AuthorizationCodeInstalledApp(flow, receiver).authorize(user);
    }

    /**
     * Loads the already-stored (encrypted) credential for this app-key <em>without</em> launching the
     * interactive browser flow. Returns {@code null} when no token has been stored for this app-key
     * (i.e. this account was never logged in with this key) — letting callers report login state
     * instead of triggering a consent prompt.
     */
    public Credential loadStoredCredential(final NetHttpTransport transport) throws IOException {
        var tokensDir = activeCredDir().resolve("tokens_" + encryptedAppKey);
        if (!Files.isDirectory(tokensDir)) return null; // never logged in with this app-key
        var flow = new GoogleAuthorizationCodeFlow.Builder(
            transport, GsonFactory.getDefaultInstance(), clientSecrets(), scopes
        )
        .setDataStoreFactory(tokensDataStore())
        .setAccessType(accessType)
        .build();
        return flow.loadCredential(user);
    }

    /**
     * Deletes the stored (encrypted) token folder for this app-key so the next {@link #credentials}
     * call triggers a fresh browser consent. Use this to re-grant after the requested scopes change
     * (a stored token keeps its original scopes; the library reuses it without re-prompting). Returns
     * {@code true} if a token folder existed and was removed.
     */
    public boolean deleteStoredCredential() throws IOException {
        var tokensDir = activeCredDir().resolve("tokens_" + encryptedAppKey);
        if (!Files.isDirectory(tokensDir)) return false;
        try (var paths = Files.walk(tokensDir)) {
            paths.sorted(java.util.Comparator.reverseOrder())
                 .forEach(p -> { try { Files.delete(p); } catch (IOException e) { throw new UncheckedIOException(e); } });
        }
        return true;
    }
}
