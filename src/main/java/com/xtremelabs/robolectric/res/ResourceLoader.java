package com.xtremelabs.robolectric.res;

import android.R;
import android.content.Context;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import com.xtremelabs.robolectric.util.PropertiesHelper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

import static com.xtremelabs.robolectric.Robolectric.shadowOf;

public class ResourceLoader {
    private static final FileFilter MENU_DIR_FILE_FILTER = new FileFilter() {
        @Override
        public boolean accept(File file) {
            return isMenuDirectory(file.getPath());
        }
    };
    private static final FileFilter LAYOUT_DIR_FILE_FILTER = new FileFilter() {
        @Override
        public boolean accept(File file) {
            return isLayoutDirectory(file.getPath());
        }
    };

    private List<File> resourcePath;
    private File assetsDir;
    private int sdkVersion;

    private final ResourceExtractor resourceExtractor;
    private ViewLoader viewLoader;
    private MenuLoader menuLoader;
    private StringResourceLoader stringResourceLoader;
    private StringArrayResourceLoader stringArrayResourceLoader;
    private ColorResourceLoader colorResourceLoader;
    private final List<RawResourceLoader> rawResourceLoaders = new ArrayList<RawResourceLoader>();
    private boolean isInitialized = false;

    // TODO: get these value from the xml resources instead [xw 20101011]
    public final Map<Integer, Integer> dimensions = new HashMap<Integer, Integer>();

    @Deprecated
    public ResourceLoader(int sdkVersion, Class rClass, File resourceDir, File assetsDir) throws Exception {
        this(sdkVersion, rClass, safeFileList(resourceDir), assetsDir);
    }

    private static List<File> safeFileList(File resourceDir) {
        return resourceDir == null ? Collections.<File> emptyList()  : Collections.singletonList(resourceDir);
    }

    public ResourceLoader(int sdkVersion, Class rClass, List<File> resourcePath, File assetsDir) throws Exception {
        this.sdkVersion = sdkVersion;
        this.assetsDir = assetsDir;
        resourceExtractor = new ResourceExtractor();
        resourceExtractor.addLocalRClass(rClass);
        resourceExtractor.addSystemRClass(R.class);

        this.resourcePath = Collections.unmodifiableList(resourcePath);
    }

    private void init() {
        if (isInitialized) {
            return;
        }

        if (!resourcePath.isEmpty()) {
            try {
                File systemResourceDir = getSystemResourceDir(getPathToAndroidResources());
                File systemValueResourceDir = getValueResourceDir(systemResourceDir);
                AttrResourceLoader attrResourceLoader = new AttrResourceLoader(resourceExtractor);

                stringResourceLoader = new StringResourceLoader(resourceExtractor);
                stringArrayResourceLoader = new StringArrayResourceLoader(resourceExtractor, stringResourceLoader);
                colorResourceLoader = new ColorResourceLoader(resourceExtractor);
                viewLoader = new ViewLoader(resourceExtractor, attrResourceLoader);
                menuLoader = new MenuLoader(resourceExtractor, attrResourceLoader);

                loadStringResources(systemValueResourceDir, stringResourceLoader, true);
                loadValueResources(systemValueResourceDir, stringArrayResourceLoader, colorResourceLoader, attrResourceLoader, true);
                loadViewResources(systemResourceDir, viewLoader);

                for (File resourceDir : resourcePath) {
                    File localValueResourceDir = getValueResourceDir(resourceDir);
                    RawResourceLoader rawResourceLoader = new RawResourceLoader(resourceExtractor, resourceDir);

                    loadStringResources(localValueResourceDir, stringResourceLoader, false);
                    loadValueResources(localValueResourceDir, stringArrayResourceLoader, colorResourceLoader, attrResourceLoader, false);
                    loadViewResources(resourceDir, viewLoader);
                    loadMenuResources(resourceDir, menuLoader);

                    rawResourceLoaders.add(rawResourceLoader);
                }

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        isInitialized = true;
    }

    private File getSystemResourceDir(String pathToAndroidResources) {
        return pathToAndroidResources != null ? new File(pathToAndroidResources) : null;
    }

    private void loadStringResources(File resourceDir, StringResourceLoader stringResourceLoader, boolean system) throws Exception {
        DocumentLoader stringResourceDocumentLoader = new DocumentLoader(stringResourceLoader);
        loadValueResourcesFromDirs(stringResourceDocumentLoader, resourceDir, system);
    }

    private void loadValueResources(File resourceDir, StringArrayResourceLoader stringArrayResourceLoader, ColorResourceLoader colorResourceLoader, AttrResourceLoader attrResourceLoader, boolean system) throws Exception {
        DocumentLoader valueResourceLoader = new DocumentLoader(stringArrayResourceLoader, colorResourceLoader, attrResourceLoader);
        loadValueResourcesFromDirs(valueResourceLoader, resourceDir, system);
    }

    private void loadViewResources(File resourceDir, ViewLoader viewLoader) throws Exception {
        DocumentLoader viewDocumentLoader = new DocumentLoader(viewLoader);
        loadLayoutResourceXmlSubDirs(viewDocumentLoader, resourceDir);
    }

    private void loadMenuResources(File xmlResourceDir, MenuLoader menuLoader) throws Exception {
        DocumentLoader menuDocumentLoader = new DocumentLoader(menuLoader);
        loadMenuResourceXmlDirs(menuDocumentLoader, xmlResourceDir);
    }

    private void loadLayoutResourceXmlSubDirs(DocumentLoader layoutDocumentLoader, File xmlResourceDir) throws Exception {
        if (xmlResourceDir != null) {
            layoutDocumentLoader.loadResourceXmlDirs(xmlResourceDir.listFiles(LAYOUT_DIR_FILE_FILTER));
        }
    }

    private void loadMenuResourceXmlDirs(DocumentLoader menuDocumentLoader, File xmlResourceDir) throws Exception {
        if (xmlResourceDir != null) {
            menuDocumentLoader.loadResourceXmlDirs(xmlResourceDir.listFiles(MENU_DIR_FILE_FILTER));
        }
    }

    private void loadValueResourcesFromDirs(DocumentLoader documentLoader, File resourceDir, boolean system) throws Exception {
        if (system) {
            loadSystemResourceXmlDir(documentLoader, resourceDir);
        } else {
            loadValueResourcesFromDir(documentLoader, resourceDir);
        }
    }

    private void loadValueResourcesFromDir(DocumentLoader documentloader, File xmlResourceDir) throws Exception {
        if (xmlResourceDir != null && xmlResourceDir.exists()) {
            documentloader.loadResourceXmlDir(xmlResourceDir);
        }
    }

    private void loadSystemResourceXmlDir(DocumentLoader documentLoader, File stringResourceDir) throws Exception {
        if (stringResourceDir != null) {
            documentLoader.loadSystemResourceXmlDir(stringResourceDir);
        }
    }

    private File getValueResourceDir(File xmlResourceDir) {
        return xmlResourceDir != null ? new File(xmlResourceDir, "values") : null;
    }

    private String getPathToAndroidResources() {
        String resourcePath;
        if ((resourcePath = getAndroidResourcePathFromLocalProperties()) != null) {
            return resourcePath;
        } else if ((resourcePath = getAndroidResourcePathFromSystemEnvironment()) != null) {
            return resourcePath;
        } else if ((resourcePath = getAndroidResourcePathByExecingWhichAndroid()) != null) {
            return resourcePath;
        }

        System.out.println("WARNING: Unable to find path to Android SDK");
        return null;
    }

    private String getAndroidResourcePathFromLocalProperties() {
        // Hand tested
        // This is the path most often taken by IntelliJ
        File rootDir = resourcePath.get(0).getParentFile();
        String localPropertiesFileName = "local.properties";
        File localPropertiesFile = new File(rootDir, localPropertiesFileName);
        if (!localPropertiesFile.exists()) {
            localPropertiesFile = new File(localPropertiesFileName);
        }
        if (localPropertiesFile.exists()) {
            Properties localProperties = new Properties();
            try {
                localProperties.load(new FileInputStream(localPropertiesFile));
                PropertiesHelper.doSubstitutions(localProperties);
                String sdkPath = localProperties.getProperty("sdk.dir");
                if (sdkPath != null) {
                    return getResourcePathFromSdkPath(sdkPath);
                }
            } catch (IOException e) {
                // fine, we'll try something else
            }
        }
        return null;
    }

    private String getAndroidResourcePathFromSystemEnvironment() {
        // Hand tested
        String resourcePath = System.getenv().get("ANDROID_HOME");
        if (resourcePath != null) {
            return new File(resourcePath, getAndroidResourceSubPath()).toString();
        }
        return null;
    }

    private String getAndroidResourcePathByExecingWhichAndroid() {
        // Hand tested
        // Should always work from the command line. Often fails in IDEs because they don't pass the full PATH in the environment
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"which", "android"});
            String sdkPath = new BufferedReader(new InputStreamReader(process.getInputStream())).readLine();
            if (sdkPath != null && sdkPath.endsWith("tools/android")) {
                return getResourcePathFromSdkPath(sdkPath.substring(0, sdkPath.indexOf("tools/android")));
            }
        } catch (IOException e) {
            // fine we'll try something else
        }
        return null;
    }

    private String getResourcePathFromSdkPath(String sdkPath) {
        File androidResourcePath = new File(sdkPath, getAndroidResourceSubPath());
        return androidResourcePath.exists() ? androidResourcePath.toString() : null;
    }

    private String getAndroidResourceSubPath() {
        return "platforms/android-" + sdkVersion + "/data/res";
    }

    static boolean isLayoutDirectory(String path) {
        return path.contains(File.separator + "layout");
    }

    static boolean isMenuDirectory(String path) {
        return path.contains(File.separator + "menu");
    }

    /*
    * For tests only...
    */
    protected ResourceLoader(StringResourceLoader stringResourceLoader) {
        resourceExtractor = new ResourceExtractor();
        resourcePath = Collections.emptyList();
        this.stringResourceLoader = stringResourceLoader;
    }

    public static ResourceLoader getFrom(Context context) {
        ResourceLoader resourceLoader = shadowOf(context.getApplicationContext()).getResourceLoader();
        resourceLoader.init();
        return resourceLoader;
    }

    public String getNameForId(int viewId) {
        init();
        return resourceExtractor.getResourceName(viewId);
    }

    public View inflateView(Context context, int resource, ViewGroup viewGroup) {
        init();

        View viewNode = viewLoader.inflateView(context, resource, viewGroup);
        if (viewNode != null) return viewNode;

        throw new RuntimeException("Could not find layout " + resourceExtractor.getResourceName(resource));
    }

    public int getColorValue(int id) {
        init();

        int value = colorResourceLoader.getValue(id);
        if (value != -1) return value;

        return -1;
    }

    public String getStringValue(int id) {
        init();

        String value = stringResourceLoader.getValue(id);
        if (value != null) return value;

        return null;
    }

    public InputStream getRawValue(int id) {
        init();

        for (RawResourceLoader rawResourceLoader : rawResourceLoaders) {
            InputStream stream = rawResourceLoader.getValue(id);
            if (stream != null) return stream;
        }

        return null;
    }

    public String[] getStringArrayValue(int id) {
        init();

        String[] arrayValue = stringArrayResourceLoader.getArrayValue(id);
        if (arrayValue != null) return arrayValue;

        return null;
    }

    public void inflateMenu(Context context, int resource, Menu root) {
        init();

        if (menuLoader.inflateMenu(context, resource, root)) return;

        throw new RuntimeException("Could not find menu " + resourceExtractor.getResourceName(resource));
    }

    public File getAssetsBase() {
        return assetsDir;
    }

    public ResourceExtractor getResourceExtractor() {
        return resourceExtractor;
    }
}
