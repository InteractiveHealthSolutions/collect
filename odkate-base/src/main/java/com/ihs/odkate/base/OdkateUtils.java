package com.ihs.odkate.base;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.listeners.DiskSyncListener;
import org.odk.collect.android.tasks.DiskSyncTask;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

/**
 * Created by Maimoona on 7/17/2017.
 */

public class OdkateUtils {

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
                        String xform = convertStreamToString(in);
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

    public static Node getNextChild(Node el) {
        for (int i = 0; i < el.getChildNodes().getLength(); i++){
            if (el.getChildNodes().item(i).getNodeType() == Node.ELEMENT_NODE){
                return el.getChildNodes().item(i);
            }
        }
        return null;
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

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            Document doc = docBuilder.parse(new File(xformPath));

            // Get the element , it may not working if tag has spaces, or whatever weird characters in front...
            // it's better to use getElementsByTagName() rather than using root.getFirstChild() to get it directly.

            NodeList instanceSet = (NodeList) XPathEvaluator.query("//model/instance[1]", doc, XPathConstants.NODESET);

            if (instanceSet == null || instanceSet.getLength() == 0){
                throw new RuntimeException("No node found at xpath '//model/instance[1]'. Make sure that xform has proper schema");
            }

            Node instance = instanceSet.item(0);

            for (String field: overrides.keySet()) {
                NodeList requiredFieldSet = (NodeList) XPathEvaluator.query(".//"+field, instance, XPathConstants.NODESET);
                if (requiredFieldSet == null || requiredFieldSet.getLength() == 0){
                    Log.w(Odkate.class.getName(), "No node found in model instance with name "+field+". Make sure that xform has proper schema");

                    Node n = doc.createElement(field);
                    Text text = doc.createTextNode(overrides.get(field));
                    n.appendChild(text);
                    getNextChild(instance).appendChild(n);
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

            // initialize StreamResult with File object to save to file where odk looks for forms
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

    public static String getFormDefinition(String formName) throws Exception {
        FileInputStream fip = new FileInputStream(new File(Collect.FORMS_PATH+"/"+formName+"-media/form_definition.json"));
        String fileContent = convertStreamToString(fip);
        fip.close();
        return fileContent;
    }

    public static Document parseXml(String xml) throws IOException, SAXException, ParserConfigurationException {
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        Document doc = docBuilder.parse(new InputSource(new StringReader(xml)));
        return doc;
    }

    public static String getUniqueValueForPath(String path, Node doc){
        Node node = (Node) XPathEvaluator.query(path, doc, XPathConstants.NODE);
        if (node == null){
            return null;
        }

        return node.getTextContent();
    }

    public static String getUniqueAttributeForPath(String path, String attribute, Node doc){
        Node n = (Node) XPathEvaluator.query(path, doc, XPathConstants.NODE);
        if (n == null){
            return null;
        }

        return n.hasAttributes()&&n.getAttributes().getNamedItem(attribute)!=null?
                n.getAttributes().getNamedItem(attribute).getNodeValue():null;
    }

    public static JSONObject createFormDefinition(String formXml) throws IOException {
        try{
            Document doc = parseXml(formXml);

            // Get the element , it may not working if tag has spaces, or whatever weird characters in front...
            // it's better to use getElementsByTagName() rather than using root.getFirstChild() to get it directly.

            NodeList instanceSet = (NodeList) XPathEvaluator.query("//model/bind", doc, XPathConstants.NODESET);

            if (instanceSet == null || instanceSet.getLength() == 0){
                throw new RuntimeException("No node found at xpath '//model/bind'. Make sure that xform has proper schema");
            }

            NodeList rootNode = (NodeList) XPathEvaluator.query("//model/instance[1]/*[1]", doc, XPathConstants.NODESET);
            String rootNodeName = rootNode.item(0).getNodeName();
            NodeList repeats = (NodeList) XPathEvaluator.query("//repeat[@nodeset]", doc, XPathConstants.NODESET);
            List<String> repeatPaths = new ArrayList<>();

            for (int i = 0; i < repeats.getLength(); i++) {
                repeatPaths.add(repeats.item(i).getAttributes().getNamedItem("nodeset").getNodeValue());
            }

            JSONArray fieldsList = xmlModelToJsonDefinition(instanceSet);
            JSONObject formD = getJsonForm(rootNodeName, fieldsList, repeatPaths);
            try{
                String bindType = "";
                if (rootNode.item(0).getAttributes() != null && rootNode.item(0).getAttributes().getNamedItem("bind_type") != null){
                    bindType = rootNode.item(0).getAttributes().getNamedItem("bind_type").getTextContent();
                }
                else {
                    bindType = rootNodeName;
                }
                formD.getJSONObject("form").put("bind_type", bindType);
            }
            catch (Exception e){
                e.printStackTrace();
            }

            try{
                for (int j = 0; j < repeatPaths.size(); j++) {
                    String spath = repeatPaths.get(j);
                    String sbindType = getUniqueAttributeForPath(spath, "bind_type", rootNode.item(0));
                    if (sbindType != null && !sbindType.isEmpty()){
                        formD.getJSONObject("form").getJSONArray("sub_forms").getJSONObject(j).put("bind_type", sbindType);
                    }
                }
            }
            catch (Exception e){
                e.printStackTrace();
            }

            try {
                formD.put("form_data_definition_version", rootNode.item(0).getAttributes().getNamedItem("version").getTextContent());
            }catch (Exception e){
                e.printStackTrace();
            }
            return formD;
        }
        catch(Exception e){
            System.err.println(e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    private static JSONArray xmlModelToJsonDefinition(NodeList nodeList) throws DOMException, JSONException {
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

    private static JSONObject getJsonForm(String rootNode, JSONArray fieldNodeList, List<String> repeatPaths) throws JSONException{
        JSONObject formDefinition= new JSONObject();
        JSONArray fields = new JSONArray();
        JSONObject subFormMap = new JSONObject();

        JSONObject field=new JSONObject();
        field.put("name", "id");
        field.put("shouldLoadValue", true);
        fields.put(field);

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
                fields.put(f);
            }
            else {
                JSONObject subF = subFormMap.has(subformPath)?subFormMap.getJSONObject(subformPath):null;
                if(subF == null){
                    JSONArray sbfields = new JSONArray();
                    JSONObject f=new JSONObject();
                    f.put("name", "id");
                    f.put("shouldLoadValue", true);
                    sbfields.put(f);

                    subF = new JSONObject();
                    String subformName = subformPath.substring(subformPath.lastIndexOf("/")+1);
                    subF.put("name", subformName);
                    subF.put("fields", sbfields);
                    subF.put("default_bind_path", "/model/instance"+subformPath+"/");
                    subF.put("bind_type", subformName);

                    subFormMap.put(subformPath, subF);
                }

                JSONObject f=new JSONObject();
                f.put("name", name);
                f.put("bind", "/model/instance"+bind);
                subF.getJSONArray("fields").put(f);
            }
        }

        JSONArray subForms = new JSONArray();

        Iterator<String> it = subFormMap.keys();
        while (it.hasNext()){
            subForms.put(subFormMap.get(it.next()));
        }

        JSONObject formData = new JSONObject();
        formData.put("bind_type", "");
        formData.put("default_bind_path", source);
        formData.put("fields", fields);
        if(subForms.length()>0) {
            formData.put("sub_forms", subForms);
        }
        formDefinition.put("form", formData);
        return formDefinition;
    }
}
