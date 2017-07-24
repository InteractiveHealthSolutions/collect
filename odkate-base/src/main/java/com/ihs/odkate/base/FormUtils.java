package com.ihs.odkate.base;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import com.google.api.client.util.StringUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.provider.InstanceProviderAPI;
import org.odk.collect.android.utilities.FileUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

import javax.xml.xpath.XPathConstants;

import static com.ihs.odkate.base.OdkateUtils.getUniqueAttributeForPath;
import static com.ihs.odkate.base.OdkateUtils.getUniqueValueForPath;
import static com.ihs.odkate.base.OdkateUtils.parseXml;

public class FormUtils {

    static FormUtils instance;
    Context mContext;

   /* private static final String relationalIdKey = "relationalid";
    private static final String databaseIdKey = "_id";
*/
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    Format formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public FormUtils(Context context){
        mContext = context;
    }

    public static FormUtils getInstance(Context ctx){
        if (instance == null){
            instance = new FormUtils(ctx);
        }
        return instance;
    }

    public JSONObject generateFormSubmisionFromXMLString(String[] bindTypes, Uri instanceUri, Context context) throws Exception {
        Cursor instanceCursor = null;
        try {
            instanceCursor = context.getContentResolver().query(instanceUri, null, null, null, null);
            if (instanceCursor.getCount() != 1) {
                Log.e(getClass().getName(), context.getString(org.odk.collect.android.R.string.bad_uri, instanceUri));
                return null;
            } else {
                instanceCursor.moveToFirst();
                String instancePath = instanceCursor.getString(instanceCursor
                        .getColumnIndex(InstanceProviderAPI.InstanceColumns.INSTANCE_FILE_PATH));
                String formName = instanceCursor.getString(instanceCursor
                        .getColumnIndex(InstanceProviderAPI.InstanceColumns.JR_FORM_ID));

                File instance = new File(instancePath);
                // convert files into a byte array
                byte[] fileBytes = FileUtils.getFileAsBytes(instance);
                return generateFormSubmisionFromXMLString(bindTypes, new String(fileBytes), formName);
            }
        } finally {
            if (instanceCursor != null) {
                instanceCursor.close();
            }
        }
    }
	
    public String getFormDefinitionPath(String formName) {
        return Collect.FORMS_PATH+"/"+formName+"-media/form_definition.json";
    }

    public JSONObject generateFormSubmisionFromXMLString(String[] bindTypes, String formData, String formName) throws Exception{
        Document formSubmission = parseXml(formData);

        android.util.Log.d(getClass().getName(), "XML FS : "+formData);

        // use the form_definition.json to iterate through fields
        String formDefinitionJson = OdkateUtils.getFormDefinition(formName);
        JSONObject formDefinition = new JSONObject(formDefinitionJson);

        String rootNodeKey = formSubmission.getDocumentElement().getNodeName();
        String bindType = null;
        if (bindTypes != null && bindTypes.length > 0){
            bindType = bindTypes[0];
        }
        else if (formSubmission.getDocumentElement().hasAttribute("bind_type")
                || formSubmission.getDocumentElement().hasAttribute("bindType")){
            bindType = formSubmission.getDocumentElement().getAttribute("bindType");
        }
        else {
            bindType = rootNodeKey;
        }

        JSONObject form = formDefinition.getJSONObject("form");
        form.put("bind_type", bindType);

        JSONArray populatedFieldsArray = getPopulatedFieldsForArray(form, formSubmission);

        // replace all the fields in the form
        form.put("fields", populatedFieldsArray);

        String entityId = retrieveIdForSubmission(formDefinition);

        //get the subforms
        if (form.has("sub_forms")){
            JSONArray subformDefnitions = form.getJSONArray("sub_forms");
            //get the actual sub-form data
            JSONArray subForms = new JSONArray();

            for (int j=0; j<subformDefnitions.length(); j++) {
                JSONObject subFormDefinition = subformDefnitions.getJSONObject(j);
                //get the bind path for the sub-form helps us to locate the node that holds the data in the corresponding data json
                String bindPath = subFormDefinition.getString("default_bind_path").replace("/model/instance", "");
                if (bindPath.endsWith("/")){
                    bindPath = bindPath.substring(0, bindPath.lastIndexOf("/"));
                }

                String subFormBindType = null;
                if (bindTypes != null && bindTypes.length > j+1){
                    subFormBindType = bindTypes[j+1];
                }
                else {
                    subFormBindType = getUniqueAttributeForPath(bindPath, "bind_type", formSubmission);
                }

                if (subFormBindType == null || subFormBindType.isEmpty()){
                    subFormBindType = subFormDefinition.getString("name");
                }

                subFormDefinition.put("bind_type", subFormBindType);

                NodeList subFormDataList = (NodeList) XPathEvaluator.query(bindPath, formSubmission, XPathConstants.NODESET);
                JSONArray subFormFields = getFieldsArrayForSubFormDefinition(subFormDefinition);
                JSONArray subFormInstances = new JSONArray();

                // the id of each subform is contained in the attribute of the enclosing element
                for (int i = 0; i < subFormDataList.getLength(); i++) {
                    Node subFormData = subFormDataList.item(i);
                    String relationalId = entityId;
                    String id = generateRandomUUIDString();
                    JSONObject subFormInstance = getFieldValuesForSubFormDefinition(subFormDefinition, relationalId, id, subFormData);
                    subFormInstances.put(i, subFormInstance);
                }
                subFormDefinition.put("instances", subFormInstances);
                subFormDefinition.put("fields", subFormFields);
                subForms.put(0, subFormDefinition);
            }
            // replace the subforms field with real data
            form.put("sub_forms", subForms);
        }

        String instanceId = generateRandomUUIDString();
        String formDefinitionVersionString = formDefinition.has("form_data_definition_version")
                ?formDefinition.getString("form_data_definition_version"):null;

        String clientVersion = String.valueOf(new Date().getTime());
        String instance = formDefinition.toString();
        JSONObject fs = new JSONObject();
        fs.put("instanceId", instanceId);
        fs.put("entityId", entityId);
        fs.put("formName", formName);
        fs.put("clientVersion", clientVersion);
        fs.put("formDataDefinitionVersion", formDefinitionVersionString);
        fs.put("instance", instance);
        return fs;
    }

    private String generateRandomUUIDString(){
        return UUID.randomUUID().toString();
    }

    private String retrieveIdForSubmission(JSONObject jsonObject) throws Exception{
        JSONArray fields = jsonObject.getJSONObject("form").getJSONArray("fields");
        for (int i = 0; i < fields.length(); i++){
            JSONObject field = fields.getJSONObject(i);
            if (field.has("name") && field.getString("name").equalsIgnoreCase("id")){
                return field.getString("value");
            }
        }
        return null;
    }

    private Object getObjectAtPath(String[] path, JSONObject jsonObject) throws Exception{
        JSONObject object = jsonObject;
        int i = 0;
        while (i < path.length - 1) {
            if (object.has(path[i])) {
                Object o = object.get(path[i]);
                if (o instanceof JSONObject){
                    object = object.getJSONObject(path[i]);
                }
                else if (o instanceof JSONArray){
                    object = object.getJSONArray(path[i]).getJSONObject(0);
                }
            }
            i++;
        }
        return object.has(path[i]) ? object.get(path[i]) : null;
    }

    //todo source:xxxxxxxxxxxx
    private JSONArray getPopulatedFieldsForArray(JSONObject fieldsDefinition, Document formSubmission) throws  Exception{
        String bindPath = fieldsDefinition.getString("bind_type");

        JSONArray fieldsArray = fieldsDefinition.getJSONArray("fields");

        for (int i = 0; i < fieldsArray.length(); i++){
            JSONObject item = fieldsArray.getJSONObject(i);
            if (!item.has("name"))
                continue; // skip elements without name

            String itemName = item.getString("name");
            boolean shouldLoadValue = item.has("shouldLoadValue") && item.getBoolean("shouldLoadValue");

            if (item.has("bind")){
                String pathSting = item.getString("bind").replace("/model/instance", "");
                String value = getUniqueValueForPath(pathSting, formSubmission);
                item.put("value", value);
            }

            // add source property if not available
            if (!item.has("source")){
                item.put("source", bindPath + "." +  item.getString("name"));
            }

            if (itemName.equalsIgnoreCase("id")) {
                String p = fieldsDefinition.getString("default_bind_path").replace("/model/instance", "")+"entityId";
                item.put("value", getUniqueValueForPath(p, formSubmission));
            }

           /* if (itemName.equalsIgnoreCase("id") && !isForeignIdPath(item)){
                assert entityId != null;
                item.put("value", entityId);
            }*/

            /*if(itemName.equalsIgnoreCase("start") || itemName.equalsIgnoreCase("end")){
                try {
                    boolean isEndTime = itemName.equalsIgnoreCase("end");
                    String val = item.has("value") ? item.getString("value") : sdf.format(new Date());
                    if (isEndTime){
                        val = formatter.format(new Date());
                    }else{
                        Date d = sdf.parse(val);
                        //parse the date to match OpenMRS format
                        val = formatter.format(d);
                    }
                    item.put("value", val);
                }catch(Exception e){
                    e.printStackTrace();
                }
            }*/
        }
        return fieldsArray;
    }

    private boolean isForeignIdPath(JSONObject item) throws Exception{
        return item.has("source") && item.getString("source").split("\\.").length > 2;  // e.g ibu.anak.id
    }

//    public String retrieveValueForLinkedRecord(String link, JSONObject entityJson) {
//        try {
//            String entityRelationships = readFileFromAssetsFolder("www/form/entity_relationship.json");
//            JSONArray json = new JSONArray(entityRelationships);
//            Log.logInfo(json.toString());
//
//            JSONObject rJson = null;
//            if ((rJson = retrieveRelationshipJsonForLink(link, json)) != null) {
//                String[] path = link.split("\\.");
//                String parentTable = path[0];
//                String childTable = path[1];
//
//                String joinValueKey = parentTable.equals(rJson.getString("parent")) ? rJson.getString("from") : rJson.getString("to");
//                joinValueKey = joinValueKey.contains(".") ? joinValueKey.substring(joinValueKey.lastIndexOf(".") + 1) : joinValueKey;
//
//                String val = entityJson.getString(joinValueKey);
//
//                String joinField = parentTable.equals(rJson.getString("parent")) ? rJson.getString("to") : rJson.getString("from");
//                String sql = "select * from " + childTable + " where " + joinField + "='" + val + "'";
//                Log.logInfo(sql);
//                String dbEntity = theAppContext.formDataRepository().queryUniqueResult(sql);
//                JSONObject linkedEntityJson = new JSONObject();
//                if (dbEntity != null && !dbEntity.isEmpty()){
//                    linkedEntityJson = new JSONObject(dbEntity);
//                }
//
//                //finally retrieve the value from the child entity, need to improve or remove entirely these hacks
//                String sourceKey = link.substring(link.lastIndexOf(".") + 1);
//                if (linkedEntityJson.has(sourceKey)){
//                    return linkedEntityJson.getString(sourceKey);
//                }
//            }
//
//        }catch(Exception e){
//            e.printStackTrace();
//        }
//        return null;
//    }

    /*private static JSONObject retrieveRelationshipJsonForLink(String link,JSONArray array) throws Exception{
        for(int i = 0; i < array.length(); i++){
            JSONObject object = array.getJSONObject(i);
            if (relationShipExist(link, object)) {
                System.out.println("Relationship found ##");
                return object;
            }
        }
        return null;
    }*/

    private static boolean relationShipExist(String link, JSONObject json){
        try {
            String[] path = link.split("\\.");
            String parentTable = path[0];
            String childTable = path[1];

            String jsonParentTableString = json.getString("parent");
            String jsonChildTableString = json.getString("child");

            boolean parentToChildExist = jsonParentTableString.equals(parentTable) && jsonChildTableString.equals(childTable);
            boolean childToParentExist = jsonParentTableString.equals(childTable) && jsonChildTableString.equals(parentTable);

            if ( parentToChildExist || childToParentExist) {
                return true;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private JSONArray getFieldsArrayForSubFormDefinition(JSONObject fieldsDefinition) throws  Exception{
        JSONArray fieldsArray = fieldsDefinition.getJSONArray("fields");
        String bindPath = fieldsDefinition.getString("bind_type");

        JSONArray subFormFieldsArray = new JSONArray();

        for (int i = 0; i < fieldsArray.length(); i++){
            JSONObject field = new JSONObject();
            JSONObject item = fieldsArray.getJSONObject(i);
            if (!item.has("name"))
                continue; // skip elements without name
            field.put("name", item.getString("name"));

            if (!item.has("source")){
                field.put("source", bindPath + "." +  item.getString("name"));
            }else{
                field.put("source", bindPath + "." +  item.getString("source"));
            }

            subFormFieldsArray.put(i, field);
        }

        return subFormFieldsArray;
    }

    private JSONObject getFieldValuesForSubFormDefinition(JSONObject fieldsDefinition, String relationalId, String entityId, Node formData) throws  Exception{
        JSONArray fieldsArray = fieldsDefinition.getJSONArray("fields");

        JSONObject fieldsValues = new JSONObject();

        for (int i = 0; i < fieldsArray.length(); i++){
            JSONObject item = fieldsArray.getJSONObject(i);
            if (!item.has("name"))
                continue; // skip elements without name
            if (item.has("bind")){
                String pathString = item.getString("bind");

                String value = getUniqueValueForPath(pathString, formData);
                fieldsValues.put(item.getString("name"), value);
            }

            //TODO: generate the id for the record
            if (item.has("name") && item.getString("name").equalsIgnoreCase("id")){
                String id = entityId != null && !entityId.isEmpty() ? entityId : generateRandomUUIDString();
                fieldsValues.put(item.getString("name"), id);
            }

            //TODO: generate the relational for the record
            if (item.has("name") && item.getString("name").equalsIgnoreCase("relationalid")){
                fieldsValues.put(item.getString("name"), relationalId);
            }
        }
        return fieldsValues;
    }

    private String getValueForPath(String[] path, JSONObject jsonObject) throws Exception{
        JSONObject object = jsonObject;
        String value = null;
        int i = 0;
        while (i < path.length - 1) {
            if (object.has(path[i])) {
                Object o = object.get(path[i]);
                if (o instanceof JSONObject){
                    object = object.getJSONObject(path[i]);
                }
                else if (o instanceof JSONArray){
                    object = object.getJSONArray(path[i]).getJSONObject(0);
                }
            }
            i++;
        }
        Object valueObject = object.has(path[i]) ? object.get(path[i]) : null;

        if (valueObject == null)
            return value;
        if(valueObject instanceof JSONObject && ((JSONObject) valueObject).has("content")){
            value = ((JSONObject) object.get(path[i])).getString("content");
        }
        else if(valueObject instanceof JSONArray){
            value = ((JSONArray)valueObject).get(0).toString();
        }
        else if(!(valueObject instanceof JSONObject) ){
            value = valueObject.toString();
        }
        return value;
    }
}