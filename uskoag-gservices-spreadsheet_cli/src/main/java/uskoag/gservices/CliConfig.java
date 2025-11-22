package uskoag.gservices;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration loader/saver for SpreadsheetCli.xml
 *
 * Format:
 * <SpreadsheetCli>
 *     <permissions>
 *         <allowRead>spreadsheet_id_1</allowRead>
 *         <allowWrite name="name for user ease">spreadsheet_id_2</allowWrite>
 *     </permissions>
 * </SpreadsheetCli>
 */
public class CliConfig {

    private final Path configFile;
    private final Map<String, Permission> permissions = new HashMap<>();
    private final boolean verbose;

    public CliConfig(Path configFile, boolean verbose) throws IOException {
        this.configFile = configFile;
        this.verbose = verbose;
        loadOrCreateConfig();
    }

    private void loadOrCreateConfig() throws IOException {
        if (!Files.exists(configFile)) {
            if (verbose) {
                System.err.println("[INFO] Config file not found, creating default: " + configFile);
            }
            createDefaultConfig();
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(configFile.toFile());

            Element root = doc.getDocumentElement();
            NodeList permissionsNodes = root.getElementsByTagName("permissions");

            if (permissionsNodes.getLength() > 0) {
                Element permissionsElement = (Element) permissionsNodes.item(0);

                // Parse allowRead permissions
                NodeList allowReadNodes = permissionsElement.getElementsByTagName("allowRead");
                for (int i = 0; i < allowReadNodes.getLength(); i++) {
                    Element elem = (Element) allowReadNodes.item(i);
                    String spreadsheetId = elem.getTextContent().trim();
                    String name = elem.getAttribute("name");
                    permissions.put(spreadsheetId, new Permission(spreadsheetId, name, true, false));
                }

                // Parse allowWrite permissions (implies read)
                NodeList allowWriteNodes = permissionsElement.getElementsByTagName("allowWrite");
                for (int i = 0; i < allowWriteNodes.getLength(); i++) {
                    Element elem = (Element) allowWriteNodes.item(i);
                    String spreadsheetId = elem.getTextContent().trim();
                    String name = elem.getAttribute("name");
                    permissions.put(spreadsheetId, new Permission(spreadsheetId, name, true, true));
                }
            }

            if (verbose) {
                System.err.println("[INFO] Loaded " + permissions.size() + " permission(s) from config");
            }

        } catch (Exception e) {
            throw new IOException("Failed to load config file: " + configFile, e);
        }
    }

    private void createDefaultConfig() throws IOException {
        try {
            Files.createDirectories(configFile.getParent());

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.newDocument();

            // Create root element
            Element root = doc.createElement("SpreadsheetCli");
            doc.appendChild(root);

            // Create permissions element
            Element permissions = doc.createElement("permissions");
            root.appendChild(permissions);

            // Add example entries (commented in XML)
            doc.appendChild(doc.createComment(" Example: <allowRead>spreadsheet_id_1</allowRead> "));
            doc.appendChild(doc.createComment(" Example: <allowWrite name=\"My Sheet\">spreadsheet_id_2</allowWrite> "));

            // Write to file
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            var transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(configFile.toFile());
            transformer.transform(source, result);

            if (verbose) {
                System.err.println("[INFO] Created default config file: " + configFile);
            }

        } catch (Exception e) {
            throw new IOException("Failed to create default config file", e);
        }
    }

    public boolean hasPermission(String spreadsheetId, String operation) {
        Permission perm = permissions.get(spreadsheetId);
        if (perm == null) {
            return false;
        }

        return switch (operation.toLowerCase()) {
            case "read" -> perm.canRead;
            case "write" -> perm.canWrite;
            default -> false;
        };
    }

    public void addPermission(String spreadsheetId, String name, boolean read, boolean write) {
        permissions.put(spreadsheetId, new Permission(spreadsheetId, name, read, write));
    }

    public void saveConfig() throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.newDocument();

            // Create root
            Element root = doc.createElement("SpreadsheetCli");
            doc.appendChild(root);

            // Create permissions
            Element permissionsElement = doc.createElement("permissions");
            root.appendChild(permissionsElement);

            // Add each permission
            for (Permission perm : permissions.values()) {
                if (perm.canWrite) {
                    Element elem = doc.createElement("allowWrite");
                    if (perm.name != null && !perm.name.isEmpty()) {
                        elem.setAttribute("name", perm.name);
                    }
                    elem.setTextContent(perm.spreadsheetId);
                    permissionsElement.appendChild(elem);
                } else if (perm.canRead) {
                    Element elem = doc.createElement("allowRead");
                    if (perm.name != null && !perm.name.isEmpty()) {
                        elem.setAttribute("name", perm.name);
                    }
                    elem.setTextContent(perm.spreadsheetId);
                    permissionsElement.appendChild(elem);
                }
            }

            // Write to file
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            var transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(configFile.toFile());
            transformer.transform(source, result);

        } catch (Exception e) {
            throw new IOException("Failed to save config file", e);
        }
    }

    private static class Permission {
        final String spreadsheetId;
        final String name;
        final boolean canRead;
        final boolean canWrite;

        Permission(String spreadsheetId, String name, boolean canRead, boolean canWrite) {
            this.spreadsheetId = spreadsheetId;
            this.name = name;
            this.canRead = canRead;
            this.canWrite = canWrite;
        }
    }
}
