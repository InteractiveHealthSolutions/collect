package com.ihs.odkate.base.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.AssetManager;
import android.util.Log;

import com.ihs.odkate.base.Odkate;
import com.ihs.odkate.base.R;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.listeners.DiskSyncListener;
import org.odk.collect.android.tasks.DiskSyncTask;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.SAXException;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPathConstants;

/**
 * Created by Maimoona on 7/17/2017.
 */

public class OdkateUtils {

    public static void showErrorDialog(Context context, String message){
        AlertDialog.Builder alertb = new AlertDialog.Builder(context);
        alertb.setMessage(message);
        alertb.setCancelable(true);

        alertb.setPositiveButton(
                R.string.data_saved_ok,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });

        AlertDialog alert = alertb.create();
        alert.show();
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
            Log.i(context.getClass().getName(), "Starting new disk sync task");

            mDiskSyncTask = new DiskSyncTask();
            mDiskSyncTask.setDiskSyncListener(diskSyncCompleteListener);
            mDiskSyncTask.execute((Void[]) null);
        }
    }

    public static void copyAssetForms(Context context) {
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

                    // copy xform to form dir
                    copyAssetFile(context, assetPath, odkPath);

                    // copy xform to form media dir to keep backup of original xform to prevent field overrides affecting original version
                    copyAssetFile(context, assetPath, odkMediaPath + File.separator + assets[i]);

                    // copy media files
                    String[] mediaAssets = assetManager.list(assetMediaPath);
                    for (String med : mediaAssets) {
                        copyAssetFile(context, assetMediaPath + File.separator + med, odkMediaPath + File.separator + med);
                    }

                    // copy form_definition.json
                    InputStream in = null;
                    Writer out = null;
                    try {
                        in = assetManager.open(assetPath);
                        String xform = IOUtils.toString(in);
                        JSONObject formDefinition = createFormDefinition(xform);

                        out = new BufferedWriter(new FileWriter(odkMediaPath + File.separator + "form_definition.json", false));
                        out.write(formDefinition.toString().replace("\\/", "/"));

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

        // must run DiskFormLoader to update ODK form db to make sure forms exist or get removed
        refreshFormIndices(context,  new DiskSyncListener() {
            @Override
            public void syncComplete(String result) {
                Log.v(getClass().getName(), "DiskSync COMPLETED "+result);
            }
        });
    }

    public static void copyAssetFile(Context context, String from, String to) {
        AssetManager assetManager = context.getAssets();

        InputStream in = null;
        OutputStream out = null;
        try {
            in = assetManager.open(from);
            String newFileName = to;
            out = new FileOutputStream(newFileName, false);

            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            in.close();
            out.flush();
            out.close();
        } catch (Exception e) {
            Log.e("tag", e.getMessage());
        }
    }

    public void writeToExternalSheet(){
     /* todo how to write  to external sheet
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
        }*/
    }

    public static void copyXFormToBin(String formId, String entityId, Map<String, String> overrides) {
        try {
            if (overrides == null){
                overrides = new HashMap<>();
            }

            if (entityId == null || entityId.trim().isEmpty()){
                entityId = UUID.randomUUID().toString();
            }

            overrides.put("entityId", entityId);

            // pick xform from media backup to flush overrides
            String xformPath = Collect.FORMS_PATH+"/"+formId+"-media/"+formId+".xml";

            Document doc = XmlUtils.readXml(xformPath);

            NodeList instanceSet = (NodeList) XmlUtils.query("//model/instance[1]", doc, XPathConstants.NODESET);

            if (instanceSet == null || instanceSet.getLength() == 0){
                throw new RuntimeException("No node found at xpath '//model/instance[1]'. Make sure that xform has proper schema");
            }

            Node instance = instanceSet.item(0);

            for (String field: overrides.keySet()) {
                NodeList requiredFieldSet = (NodeList) XmlUtils.query(".//"+field, instance, XPathConstants.NODESET);
                if (requiredFieldSet == null || requiredFieldSet.getLength() == 0){
                    Log.w(Odkate.class.getName(), "No node found in model instance with name "+field+". Make sure that xform has proper schema");

                    Node n = doc.createElement(field);
                    Text text = doc.createTextNode(overrides.get(field));
                    n.appendChild(text);
                    XmlUtils.getNextChild(instance).appendChild(n);
                    continue;
                }
                else if (requiredFieldSet.getLength() > 1){
                    throw new RuntimeException("Multiple nodes found in model instance with name "+field+". Make sure that xform has proper schema");
                }

                requiredFieldSet.item(0).setTextContent(overrides.get(field));
            }

            // save to file where odk looks for forms
            XmlUtils.writeXml(doc, Collect.FORMS_PATH+"/"+formId+".xml");

        } catch (ParserConfigurationException pce) {
            pce.printStackTrace();
        } catch (TransformerException tfe) {
            tfe.printStackTrace();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        } catch (SAXException sae) {
            sae.printStackTrace();
        }
    }

    public static String getFormDefinition(String formName) throws Exception {
        return IOUtils.toString(new FileReader(getFormDefinitionPath(formName)));
    }


    public static String getFormDefinitionPath(String formName) {
        return Collect.FORMS_PATH+"/"+formName+"-media/form_definition.json";
    }

    public static JSONObject createFormDefinition(String formXml) throws IOException {
        try{
            Document doc = XmlUtils.parseXml(formXml);

            NodeList instanceSet = (NodeList) XmlUtils.query("//model/bind", doc, XPathConstants.NODESET);

            if (instanceSet == null || instanceSet.getLength() == 0){
                throw new RuntimeException("No node found at xpath '//model/bind'. Make sure that xform has proper schema");
            }

            NodeList rootNode = (NodeList) XmlUtils.query("//model/instance[1]/*[1]", doc, XPathConstants.NODESET);
            String rootNodeName = rootNode.item(0).getNodeName();

            NodeList repeats = (NodeList) XmlUtils.query("//repeat[@nodeset]", doc, XPathConstants.NODESET);
            List<String> repeatPaths = new ArrayList<>();

            for (int i = 0; i < repeats.getLength(); i++) {
                repeatPaths.add(repeats.item(i).getAttributes().getNamedItem("nodeset").getNodeValue());
            }

            JSONArray fieldsList = xmlModelToJsonFieldDefinition(instanceSet);
            JSONObject form = getJsonForm(rootNodeName, fieldsList, repeatPaths);
            try{
                String bindType = XmlUtils.getUniqueAttributeForPath("/"+form.getString("default_bind_path"), "bind_type", doc);
                if(bindType != null && !bindType.isEmpty()) {
                    form.put("bind_type", bindType);
                }
            } catch (Exception e){
                e.printStackTrace();
            }

            try{
                for (int j = 0; j < repeatPaths.size(); j++) {
                    String sbindType = XmlUtils.getUniqueAttributeForPath("/"+repeatPaths.get(j), "bind_type", doc);
                    if (sbindType != null && !sbindType.isEmpty()){
                        form.getJSONArray("sub_forms").getJSONObject(j).put("bind_type", sbindType);
                    }
                }
            } catch (Exception e){
                e.printStackTrace();
            }

            JSONObject formDefinition = new JSONObject();
            formDefinition.put("form", form);
            formDefinition.put("form_data_definition_version", XmlUtils.getUniqueAttributeForPath("/"+form.getString("default_bind_path"), "version", doc));

            return formDefinition;
        }
        catch(Exception e){
            e.printStackTrace();
        }
        return null;
    }

    private static JSONArray xmlModelToJsonFieldDefinition(NodeList nodeList) throws DOMException, JSONException {
        JSONArray dataArr = new JSONArray();
        for (int count = 0; count < nodeList.getLength(); count++) {
            Node tempNode = nodeList.item(count);

            if (tempNode.getNodeType() == Node.ELEMENT_NODE) {
                if (tempNode.hasChildNodes() && tempNode.getChildNodes().getLength() > 1) {
                    throw new RuntimeException("Bind has multiple children nodes. Weird schema for Xform");
                }
                else {
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

    private static void addFormFieldObject(JSONObject form, JSONObject fieldNode) throws JSONException {
        if (!form.has("fields")){
            form.put("fields", new JSONArray());
        }

        JSONObject field = new JSONObject();
        field.put("name", fieldNode.getString("name"));
        field.put("bind", "/model/instance"+fieldNode.getString("bind"));

        form.getJSONArray("fields").put(field);
    }

    private static JSONObject createFormObject(String rootNode, String bindPath) throws JSONException {
        if (bindPath.startsWith("/")){
            bindPath = bindPath.substring(1);
        }

        JSONObject form = new JSONObject();
        form.put("name", rootNode);//update this before saving
        form.put("bind_type", rootNode);//update this before saving
        form.put("default_bind_path", "/model/instance/"+bindPath);
        form.put("fields", new JSONArray());

        JSONObject field=new JSONObject();
        field.put("name", "id");
        field.put("shouldLoadValue", true);

        form.getJSONArray("fields").put(field);

        return form;
    }

    private static String getRepeatPath(JSONObject field, List<String> repeatPaths) throws JSONException {
        String bind = field.getString("bind");
        for (String rp : repeatPaths) {
            if(bind.startsWith(rp)){
                return rp;
            }
        }
        return null;
    }

    private static JSONObject getJsonForm(String rootNode, JSONArray fieldNodeList, List<String> repeatPaths) throws JSONException{
        JSONObject subFormMap = new JSONObject();

        JSONObject form = createFormObject(rootNode, rootNode);

        for (int i = 0; i < fieldNodeList.length(); i++) {
            JSONObject fieldNode = fieldNodeList.getJSONObject(i);

            String subformPath = getRepeatPath(fieldNode, repeatPaths);

            if(subformPath == null){
                addFormFieldObject(form, fieldNode);
            }
            else {
                JSONObject subF = subFormMap.has(subformPath)?subFormMap.getJSONObject(subformPath):null;
                if(subF == null){
                    String subformName = subformPath.substring(subformPath.lastIndexOf("/")+1);
                    subF = createFormObject(subformName, subformPath);

                    subFormMap.put(subformPath, subF);
                }

                addFormFieldObject(subF, fieldNode);
            }
        }

        JSONArray subForms = new JSONArray();

        Iterator<String> it = subFormMap.keys();
        while (it.hasNext()){
            subForms.put(subFormMap.get(it.next()));
        }

        if(subForms.length()>0) {
            form.put("sub_forms", subForms);
        }
        return form;
    }
}
