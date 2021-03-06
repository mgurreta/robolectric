package org.robolectric.res;

import com.ximpleware.VTDNav;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class XmlLoader {
  private static final DocumentBuilderFactory documentBuilderFactory;
  static {
    documentBuilderFactory = DocumentBuilderFactory.newInstance();
    documentBuilderFactory.setNamespaceAware(true);
    documentBuilderFactory.setIgnoringComments(true);
    documentBuilderFactory.setIgnoringElementContentWhitespace(true);
  }

  private DocumentBuilder documentBuilder;

  synchronized public Document parse(FsFile xmlFile) {
    InputStream inputStream = null;
    try {
      if (documentBuilder == null) {
        documentBuilder = documentBuilderFactory.newDocumentBuilder();
      }
      inputStream = xmlFile.getInputStream();
      return documentBuilder.parse(inputStream);
    } catch (ParserConfigurationException | IOException | SAXException e) {
      throw new RuntimeException(e);
    } finally {
      if (inputStream != null) try {
        inputStream.close();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  protected void processResourceXml(FsFile xmlFile, VTDNav vtdNav, String packageName) {
    processResourceXml(xmlFile, new XpathResourceXmlLoader.XmlNode(vtdNav), new XmlContext(packageName, xmlFile));
  }

  protected abstract void processResourceXml(FsFile xmlFile, XpathResourceXmlLoader.XmlNode xmlNode, XmlContext xmlContext);

  public static class XmlContext {
    private static final Pattern DIR_QUALIFIER_PATTERN = Pattern.compile("^[^-]+(?:-(.*))?$");

    private final String packageName;
    private final FsFile xmlFile;

    public XmlContext(String packageName, FsFile xmlFile) {
      this.packageName = packageName;
      this.xmlFile = xmlFile;
    }

    public String getPackageName() {
      return packageName;
    }

    public String getDirPrefix() {
      String parentDir = xmlFile.getParent().getName();
      return parentDir.split("-")[0];
    }

    public String getQualifiers() {
      FsFile parentDir = xmlFile.getParent();
      if (parentDir == null) {
        return "";
      } else {
        String parentDirName = parentDir.getName();
        Matcher matcher = DIR_QUALIFIER_PATTERN.matcher(parentDirName);
        if (!matcher.find()) throw new IllegalStateException(parentDirName);
        return matcher.group(1);
      }
    }

    public FsFile getXmlFile() {
      return xmlFile;
    }

    @Override public String toString() {
      return "XmlContext{" +
          "packageName='" + packageName + '\'' +
          ", xmlFile=" + xmlFile +
          '}';
    }
  }
}
