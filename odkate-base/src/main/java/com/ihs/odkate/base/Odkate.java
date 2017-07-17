package com.ihs.odkate.base;

import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.android.gms.analytics.GoogleAnalytics;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.dao.FormsDao;
import org.odk.collect.android.listeners.DiskSyncListener;
import org.odk.collect.android.preferences.AdminKeys;
import org.odk.collect.android.preferences.AdminPreferencesActivity;
import org.odk.collect.android.preferences.PreferenceKeys;
import org.odk.collect.android.provider.FormsProviderAPI;
import org.odk.collect.android.tasks.DiskSyncTask;
import org.odk.collect.android.utilities.ApplicationConstants;
import org.odk.collect.android.utilities.ToastUtils;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import au.com.bytecode.opencsv.CSVWriter;
import timber.log.Timber;

/**
 * Created by Maimoona on 7/14/2017.
 */

public class Odkate extends Collect{
    public static final int ODK_FORM_ENTRY_REQUEST_CODE = 11;

    /** setup ODK whenever application is launched. This should cover everything ODK does in its init Activities
     * @param context
     */
    public static void setupODK(Activity context){
        // must be at the beginning of any activity that can be called from an external intent
        Collect.createODKDirs();

        // get the shared preferences object
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(Collect.getInstance());
        SharedPreferences.Editor editor = sharedPreferences.edit();

        // get the package info object with version number
        PackageInfo packageInfo = null;
        try {
            packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            Timber.e(e, "Unable to get package info");
        }

        boolean firstRun = sharedPreferences.getBoolean(PreferenceKeys.KEY_FIRST_RUN, true);

        // if you've increased version code, then update the version number and set firstRun to true
        if (sharedPreferences.getLong(PreferenceKeys.KEY_LAST_VERSION, 0) < packageInfo.versionCode) {
            editor.putLong(PreferenceKeys.KEY_LAST_VERSION, packageInfo.versionCode);

            firstRun = true;
        }
        firstRun = true;//// TODO: 7/14/2017
        // do all the first run things
        if (firstRun) {
            editor.putBoolean(PreferenceKeys.KEY_FIRST_RUN, false);

            editor.putString(PreferenceKeys.KEY_NAVIGATION, PreferenceKeys.NAVIGATION_BUTTONS);
            editor.putBoolean(PreferenceKeys.KEY_COMPLETED_DEFAULT, true);
            editor.putBoolean(AdminKeys.KEY_MARK_AS_FINALIZED, false);
            editor.putBoolean("delete_saved", false);//// TODO: 7/14/2017
        }

        editor.commit();

        // as specified in MainActivity of ODK-collect the settings should be updatable via collect.settings
        // to override settings again, this file should be resent and pasted.
        // This code after loading and updating settings deletes this file.
        File f = new File(Collect.ODK_ROOT + "/collect.settings");
        if (f.exists()) {
            boolean success = loadSharedPreferencesFromFile(f);
            if (success) {
                ToastUtils.showLongToast(org.odk.collect.android.R.string.settings_successfully_loaded_file_notification);
                f.delete();
            } else {
                ToastUtils.showLongToast(org.odk.collect.android.R.string.corrupt_settings_file_notification);
            }
        }

        // This flag must be set each time the app starts up
        // app crashes if we do set it up GoogleAnalytics();

        // copy forms from assets/forms to odk/forms so that collect can make use of it
        copyForms(context);

        // must run DiskFormLoader to update ODK form db to make sure forms exist or get removed
        refreshFormIndices(context,  new DiskSyncListener() {
            @Override
            public void syncComplete(String result) {
                Log.v(getClass().getName(), "DiskSync COMPLETED "+result);
            }
        });
    }

    // todo see where and how it update app about sync; Also every time forms are updated it should run itself;
    // or if app runs first time

    /**
     * This should be called on every time application is initialized or
     * odk forms on disk are updated by an automated service or anything else
     * @param context
     * @param diskSyncCompleteListener
     */
    public static void refreshFormIndices(Context context, DiskSyncListener diskSyncCompleteListener){
        DiskSyncTask mDiskSyncTask = (DiskSyncTask) (context instanceof Activity?
                ((Activity)context).getLastNonConfigurationInstance() : null);
        if (mDiskSyncTask == null) {
            Timber.i("Starting new disk sync task");
            mDiskSyncTask = new DiskSyncTask();

            mDiskSyncTask.setDiskSyncListener(diskSyncCompleteListener);
            mDiskSyncTask.execute((Void[]) null);
        }
    }

    // onListItemClick of org.odk.collect.android.activities.FormChooserList explains how form is loaded
    public static void launchODKForm(long id, Activity context){
        Uri formUri = ContentUris.withAppendedId(FormsProviderAPI.FormsColumns.CONTENT_URI, id);

        Log.v(Odkate.class.getName(), "Launching URI "+formUri);

        Intent intent = new Intent(Intent.ACTION_EDIT, formUri);
        intent.putExtra(ApplicationConstants.BundleKeys.FORM_MODE, ApplicationConstants.FormModes.EDIT_SAVED);
        context.startActivityForResult(intent, ODK_FORM_ENTRY_REQUEST_CODE);
    }

    public static void launchODKForm(String formId, Activity context, Map<String, String> overrides) throws IllegalAccessException, IOException {
        if (overrides != null && overrides.size() > 0){
            setOdkDefaults(formId, overrides);

            overrides.put("id_key", "current");// add key to refer current record to pull data from.
            // leaving room for development to pull data from related or other entities incase needed

            File root = new File(Collect.FORMS_PATH+"/"+formId+"-media");
            if (!root.exists()) {
                root.mkdirs();
            }
            File existing = new File(root, "existing.csv");
            existing.createNewFile(); // if file already exists will do nothing

            try (CSVWriter writer = new CSVWriter(new FileWriter(existing, false))) {
                String[] keysArray = new String[overrides.keySet().size()];
                String[] valuesArray = new String[overrides.values().size()];
                int counter = 0;
                for (Map.Entry<String, String> entry : overrides.entrySet()) {
                    keysArray[counter] = entry.getKey();
                    valuesArray[counter] = entry.getValue();
                    counter++;
                }
                writer.writeNext(keysArray);
                writer.writeNext(valuesArray);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        Cursor formcursor = new FormsDao().getFormsCursorForFormId(formId);
        try {
            if (formcursor.getCount() > 0) {
                formcursor.moveToFirst();

                launchODKForm(formcursor.getLong(formcursor.getColumnIndex(FormsProviderAPI.FormsColumns._ID)), context);
            }
            else throw new IllegalAccessException("Form with given id ("+formId+") not exists. Make sure application life cycle is managed properly");
        }
        finally {
            formcursor.close();
        }
    }

    public static void setOdkDefaults(String formId, Map<String, String> overrides) {
        try {
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            Document doc = docBuilder.parse(new File(Collect.FORMS_PATH+"/"+formId+".xml"));

            // Get the element , it may not working if tag has spaces, or whatever weird characters in front...
            // it's better to use getElementsByTagName() rather than using root.getFirstChild() to get it directly.

            XPath xPath =  XPathFactory.newInstance().newXPath();
            NodeList instanceSet = (NodeList) xPath.compile("//model/instance[1]").evaluate(doc, XPathConstants.NODESET);

            if (instanceSet == null || instanceSet.getLength() == 0){
                throw new RuntimeException("No node found at xpath '//model/instance[1]'. Make sure that xform has proper schema");
            }

            Node instance = instanceSet.item(0);

            for (String field: overrides.keySet()) {
                NodeList requiredFieldSet = (NodeList) xPath.compile(".//"+field).evaluate(instance, XPathConstants.NODESET);
                if (requiredFieldSet == null || requiredFieldSet.getLength() == 0){
                    Log.w(Odkate.class.getName(), "No node found in model instance with name "+field+". Make sure that xform has proper schema");
                    continue;
                }
                else if (requiredFieldSet.getLength() > 1){
                    throw new RuntimeException("Multiple nodes found in model instance with name "+field+". Make sure that xform has proper schema");
                }

                requiredFieldSet.item(0).setTextContent(overrides.get(field));
            }

            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "no");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");

            // initialize StreamResult with File object to save to file
            StreamResult result = new StreamResult(new FileOutputStream(Collect.FORMS_PATH+"/"+formId+".xml"));
            DOMSource source = new DOMSource(doc);
            transformer.transform(source, result);

            System.out.println("Done");

        } catch (ParserConfigurationException pce) {
            pce.printStackTrace();
        } catch (TransformerException tfe) {
            tfe.printStackTrace();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        } catch (SAXException sae) {
            sae.printStackTrace();
        } catch (XPathExpressionException e) {
            e.printStackTrace();
        }
    }

    public static void copyForms(Context context) {
        AssetManager assetManager = context.getAssets();
        String assets[] = null;
        try {
            assets = assetManager.list("xforms");
            String formsPath = Collect.FORMS_PATH;
            File dir = new File(formsPath);
            if (!dir.exists())
                dir.mkdir();

            for (int i = 0; i < assets.length; ++i) {
                if (assets[i].endsWith(".xml")) {// read xml forms only
                    String assetPath = "xforms" + File.separator + assets[i];
                    String odkPath = formsPath + File.separator + assets[i];
                    String assetMediaPath = assetPath.replace(".xml","")+ "-media";
                    String odkMediaPath = odkPath.replace(".xml","")+ "-media";

                    // create media folder
                    File odkmdir = new File(odkMediaPath);
                    if (!odkmdir.exists())
                        odkmdir.mkdir();

                    // copy xform
                    copyAssetFile(context, assetPath, odkPath);

                    // copy media files
                    String[] mediaAssets = assetManager.list(assetMediaPath);
                    for (String med : mediaAssets) {
                        copyAssetFile(context, assetMediaPath + File.separator + med, odkMediaPath + File.separator + med);
                    }

                    // copy form_definition.json
                    InputStream in = null;
                    OutputStream out = null;
                    try {
                        in = assetManager.open(assetPath);
                        String xform = convertStreamToString(in);
                        String formDefinition = getFormDefinition(xform);

                        out = new FileOutputStream(odkMediaPath + File.separator + "form_definition.json");
                        out.write(formDefinition.getBytes());

                        in.close();
                        out.flush();
                        out.close();
                    } catch (Exception e) {
                        Log.e("tag", e.getMessage());
                    }
                }
            }
        } catch (IOException ex) {
            Log.e("tag", "I/O Exception", ex);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String convertStreamToString(InputStream is) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line = null;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();
        return sb.toString();
    }

    public static String getStringFromFile (String filePath) throws Exception {
        File fl = new File(filePath);
        FileInputStream fin = new FileInputStream(fl);
        String ret = convertStreamToString(fin);
        //Make sure you close all streams.
        fin.close();
        return ret;
    }

    public static void copyAssets(Context context, String from, String to) {
        AssetManager assetManager = context.getAssets();
        String assets[] = null;
        try {
            assets = assetManager.list(from);
            if (assets.length == 0) {
                copyAssetFile(context, from, to);
            } else {
                String fullPath = to;
                File dir = new File(fullPath);
                if (!dir.exists())
                    dir.mkdir();
                for (int i = 0; i < assets.length; ++i) {
                    copyAssets(context, from + File.separator + assets[i], to + File.separator + assets[i]);
                }
            }
        } catch (IOException ex) {
            Log.e("tag", "I/O Exception", ex);
        }
    }

    private static void copyAssetFile(Context context, String from, String to) {
        AssetManager assetManager = context.getAssets();

        InputStream in = null;
        OutputStream out = null;
        try {
            in = assetManager.open(from);
            String newFileName = to;
            out = new FileOutputStream(newFileName);

            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            in.close();
            in = null;
            out.flush();
            out.close();
            out = null;
        } catch (Exception e) {
            Log.e("tag", e.getMessage());
        }
    }

    private static boolean loadSharedPreferencesFromFile(File src) {
        // this should probably be in a thread if it ever gets big
        boolean res = false;
        ObjectInputStream input = null;
        try {
            input = new ObjectInputStream(new FileInputStream(src));
            SharedPreferences.Editor prefEdit = PreferenceManager.getDefaultSharedPreferences(Collect.getInstance()).edit();
            prefEdit.clear();
            // first object is preferences
            Map<String, ?> entries = (Map<String, ?>) input.readObject();
            for (Map.Entry<String, ?> entry : entries.entrySet()) {
                Object v = entry.getValue();
                String key = entry.getKey();

                if (v instanceof Boolean) {
                    prefEdit.putBoolean(key, ((Boolean) v).booleanValue());
                } else if (v instanceof Float) {
                    prefEdit.putFloat(key, ((Float) v).floatValue());
                } else if (v instanceof Integer) {
                    prefEdit.putInt(key, ((Integer) v).intValue());
                } else if (v instanceof Long) {
                    prefEdit.putLong(key, ((Long) v).longValue());
                } else if (v instanceof String) {
                    prefEdit.putString(key, ((String) v));
                }
            }
            prefEdit.apply();

            // second object is admin options
            SharedPreferences.Editor adminEdit = Collect.getInstance().getSharedPreferences(AdminPreferencesActivity.ADMIN_PREFERENCES,
                    0).edit();
            adminEdit.clear();
            // first object is preferences
            Map<String, ?> adminEntries = (Map<String, ?>) input.readObject();
            for (Map.Entry<String, ?> entry : adminEntries.entrySet()) {
                Object v = entry.getValue();
                String key = entry.getKey();

                if (v instanceof Boolean) {
                    adminEdit.putBoolean(key, ((Boolean) v).booleanValue());
                } else if (v instanceof Float) {
                    adminEdit.putFloat(key, ((Float) v).floatValue());
                } else if (v instanceof Integer) {
                    adminEdit.putInt(key, ((Integer) v).intValue());
                } else if (v instanceof Long) {
                    adminEdit.putLong(key, ((Long) v).longValue());
                } else if (v instanceof String) {
                    adminEdit.putString(key, ((String) v));
                }
            }
            adminEdit.apply();

            res = true;
        } catch (IOException | ClassNotFoundException e) {
            Timber.e(e, "Exception while loading preferences from file due to : %s ", e.getMessage());
        } finally {
            try {
                if (input != null) {
                    input.close();
                }
            } catch (IOException ex) {
                Timber.e(ex, "Exception thrown while closing an input stream due to: %s ", ex.getMessage());
            }
        }
        return res;
    }

    // This flag must be set each time the app starts up
    private static void setupGoogleAnalytics() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(Collect
                .getInstance());
        boolean isAnalyticsEnabled = settings.getBoolean(PreferenceKeys.KEY_ANALYTICS, true);
        GoogleAnalytics googleAnalytics = GoogleAnalytics.getInstance(Collect.getInstance());
        googleAnalytics.setAppOptOut(!isAnalyticsEnabled);
    }

    public static String getFormDefinition(String formXml) throws IOException {
        try{
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            Document doc = docBuilder.parse(new InputSource(new StringReader(formXml)));

            // Get the element , it may not working if tag has spaces, or whatever weird characters in front...
            // it's better to use getElementsByTagName() rather than using root.getFirstChild() to get it directly.

            XPath xPath =  XPathFactory.newInstance().newXPath();
            NodeList instanceSet = (NodeList) xPath.compile("//model/bind").evaluate(doc, XPathConstants.NODESET);

            if (instanceSet == null || instanceSet.getLength() == 0){
                throw new RuntimeException("No node found at xpath '//model/bind'. Make sure that xform has proper schema");
            }

            NodeList rootNode = (NodeList) xPath.compile("//model/instance[1]/*[1]").evaluate(doc, XPathConstants.NODESET);
            String rootNodeName = rootNode.item(0).getNodeName();
            NodeList repeats = (NodeList) xPath.compile("//repeat[@nodeset]").evaluate(doc, XPathConstants.NODESET);
            List<String> repeatPaths = new ArrayList<>();

            for (int i = 0; i < repeats.getLength(); i++) {
                repeatPaths.add(repeats.item(i).getAttributes().getNamedItem("nodeset").getNodeValue());
            }

            JSONArray fieldsList = modelToDefinition(instanceSet);
            JSONObject formD = getForm(rootNodeName, fieldsList, repeatPaths);
            String s = formD.toString();
            return s;
        }
        catch(Exception e){
            System.err.println(e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    private static JSONArray modelToDefinition(NodeList nodeList) throws DOMException, JSONException {
        JSONArray dataArr = new JSONArray();
        for (int count = 0; count < nodeList.getLength(); count++) {
            Node tempNode = nodeList.item(count);
            if (tempNode.getNodeType() == Node.ELEMENT_NODE) {
                if (tempNode.hasChildNodes() && tempNode.getChildNodes().getLength() > 1) {
                    throw new RuntimeException("Bind has multiple children nodes. Weird schema for Xform");
                } else {
                    JSONObject dataObject = new JSONObject();
                    NamedNodeMap attribs = tempNode.getAttributes();
                    String nodeset = attribs.getNamedItem("nodeset").getNodeValue();
                    dataObject.put("name", nodeset.substring(nodeset.lastIndexOf("/")+1));
                    dataObject.put("bind", nodeset);
                    dataObject.put("type", attribs.getNamedItem("type").getNodeValue());
                    dataArr.put(dataObject);
                }
            }
        }
        return dataArr;
    }

    private static JSONObject getForm(String rootNode, JSONArray fieldNodeList, List<String> repeatPaths) throws JSONException{
        JSONObject formDefinition= new JSONObject();
        List<JSONObject> fields = new ArrayList<>();
        Map<String, JSONObject> subForms = new HashMap<>();

        JSONObject field=new JSONObject();
        field.put("name", "id");
        field.put("shouldLoadValue", true);
        fields.add(field);

        String source="/model/instance/"+rootNode+"/";

        for (int i = 0; i < fieldNodeList.length(); i++) {
            JSONObject fieldNode = fieldNodeList.getJSONObject(i);
            String bind = fieldNode.getString("bind");
            String name = fieldNode.getString("name");
            String subformPath = null;
            for (String rp : repeatPaths) {
                if(bind.startsWith(rp)){
                    subformPath = rp;
                    break;
                }
            }

            if(subformPath == null){
                JSONObject f=new JSONObject();
                f.put("name", name);
                f.put("bind", "/model/instance"+bind);
                fields.add(f);
            }
            else {
                JSONObject subF = subForms.get(subformPath);
                if(subF == null){
                    ArrayList<JSONObject> sbfields = new ArrayList<>();
                    JSONObject f=new JSONObject();
                    f.put("name", "id");
                    f.put("shouldLoadValue", true);
                    sbfields.add(f);

                    subF = new JSONObject();
                    subF.put("name", subformPath.substring(subformPath.lastIndexOf("/")+1));
                    subF.put("fields", sbfields);
                    subF.put("default_bind_path", "/model/instance"+subformPath+"/");
                    subF.put("bind_type", "");

                    subForms.put(subformPath, subF);
                }

                JSONObject f=new JSONObject();
                f.put("name", name);
                f.put("bind", "/model/instance"+bind);
                subF.getJSONArray("fields").put(f);
            }
        }

        JSONObject formData = new JSONObject();
        formData.put("bind_type", "");
        formData.put("source", source);
        formData.put("fields", fields);
        formData.put("sub_forms", subForms.values());
        formDefinition.put("form", formData);
        return formDefinition;
    }
}
