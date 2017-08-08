package com.ihs.odkate.base.utils;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;
import org.odk.collect.android.provider.InstanceProviderAPI;
import org.odk.collect.android.utilities.FileUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.Date;
import java.util.UUID;

import javax.xml.xpath.XPathConstants;

public class FormSubmissionUtils {
    private Context mContext;

    public FormSubmissionUtils(Context context){
        mContext = context;
    }

    public JSONObject generateFormSubmisionFromXMLString(String[] bindTypes, Uri instanceUri) throws Exception {
        Cursor instanceCursor = null;
        try {
            instanceCursor = mContext.getContentResolver().query(instanceUri, null, null, null, null);
            if (instanceCursor == null || instanceCursor.getCount() != 1) {
                Log.e(getClass().getName(), mContext.getString(org.odk.collect.android.R.string.bad_uri, instanceUri));
                return null;
            }
            else {
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

    public JSONObject generateFormSubmisionFromXMLString(String[] bindTypes, String formData, String formName) throws Exception{
        Document formSubmission = XmlUtils.parseXml(formData);

        android.util.Log.d(getClass().getName(), "XML FS : "+formData);

        // use the form_definition.json to iterate through fields
        String formDefinitionJson = OdkateUtils.getFormDefinition(formName);
        JSONObject formDefinition = new JSONObject(formDefinitionJson);
        JSONObject form = formDefinition.getJSONObject("form");

        if (bindTypes != null && bindTypes.length > 0){
            form.put("bind_type", bindTypes[0]);
        }

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

                if (bindTypes != null && bindTypes.length > j+1){
                    subFormDefinition.put("bind_type", bindTypes[j+1]);
                }

                NodeList subFormDataList = (NodeList) XmlUtils.query(bindPath, formSubmission, XPathConstants.NODESET);
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
        JSONObject fs = new JSONObject();
        fs.put("instanceId", instanceId);
        fs.put("entityId", entityId);
        fs.put("formName", formName);
        fs.put("clientVersion", clientVersion);
        fs.put("formDataDefinitionVersion", formDefinitionVersionString);
        fs.put("instance", formDefinition);
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

    //todo source:xxxxxxxxxxxx
    private JSONArray getPopulatedFieldsForArray(JSONObject fieldsDefinition, Document formSubmission) throws  Exception{
        String bindPath = fieldsDefinition.getString("bind_type");

        JSONArray fieldsArray = fieldsDefinition.getJSONArray("fields");

        for (int i = 0; i < fieldsArray.length(); i++){
            JSONObject item = fieldsArray.getJSONObject(i);
            if (!item.has("name"))
                continue; // skip elements without name

            String itemName = item.getString("name");

            if (item.has("bind")){
                String pathSting = item.getString("bind").replace("/model/instance", "");
                String value = XmlUtils.getUniqueValueForPath(pathSting, formSubmission);
                item.put("value", value);
            }

            // add source property if not available
            if (!item.has("source")){
                item.put("source", bindPath + "." +  item.getString("name"));
            }

            if (itemName.equalsIgnoreCase("id")) {
                String temp = fieldsDefinition.getString("default_bind_path").replace("/model/instance", "");
                if (!temp.endsWith("/")){
                    temp = temp +"/";
                }
                String p = temp+"entityId";
                item.put("value", XmlUtils.getUniqueValueForPath(p, formSubmission));
            }

//            if(item.has("value") && item.getString("value").matches("^\\d{2}.*")){
//                try {
//                    boolean isEndTime = itemName.equalsIgnoreCase("end");
//                    String val = item.has("value") ? item.getString("value") : sdf.format(new Date());
//                    if (isEndTime){
//                        val = formatter.format(new Date());
//                    }else{
//                        Date d = sdf.parse(val);
//                        //parse the date to match OpenMRS format
//                        val = formatter.format(d);
//                    }
//                    item.put("value", val);
//                }catch(Exception e){
//                    e.printStackTrace();
//                }
//            }
        }
        return fieldsArray;
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

                String value = XmlUtils.getUniqueValueForPath(pathString, formData);
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
}