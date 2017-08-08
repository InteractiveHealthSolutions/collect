package com.ihs.odkate.example;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.ihs.odkate.base.utils.FormSubmissionUtils;
import com.ihs.odkate.base.Odkate;

import org.joda.time.DateTime;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.provider.InstanceProviderAPI;
import org.odk.collect.android.utilities.FileUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private ListView m_JobListView;
    private String[] m_Filename;
    private String m_Path;
    private ArrayList<String> m_JobList;
    private Activity activity = this;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        // Setup ODK
        Odkate.setupODK(this);

        m_JobListView = (ListView) findViewById(R.id.xform_listview);
        m_Path = "file:///android_asset/";
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    protected void onResume() {
        super.onResume();

        m_JobList = new ArrayList<>();
        m_JobListView = (ListView) findViewById(R.id.xform_listview);
        try {
            m_Filename = new File(Collect.FORMS_PATH).list(new FilenameFilter() {
                @Override
                public boolean accept(File file, String s) {
                    return s.toLowerCase().endsWith(".xml");
                }
            });

            if (m_Filename != null && (null != m_JobList)) {

                for (int i = 0; i < m_Filename.length; i++) {
                    m_JobList.add(m_Filename[i]);
                }

                if ((m_Filename.length == 0) || (null == m_Filename)) {
                    m_JobList.add(0, "No sheets available");
                }
            }

            if (m_JobListView != null) {
                if (m_JobList != null) {
                    m_JobListView.setAdapter(new CustomAdapter ());
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error occurred during UI generation. "+e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }

    }

    private class CustomAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return m_JobList.size();
        }

        @Override
        public Object getItem(int i) {
            return m_JobList.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(final int i, View convertView, ViewGroup viewGroup) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.form_item, viewGroup, false);
            }

            TextView tv = ((TextView) convertView.findViewById(R.id.form_item_name));
            tv.setText((i+1)+"  -  "+getItem(i).toString());

            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    try {
                        Map<String, String> map = new HashMap<>();
                        map.put("provider_uc", "MY UC"+new Random().nextInt(99));
                        map.put("provider_town", "MY TOWN"+new Random().nextInt(99));
                        map.put("provider_city", "MY CITY"+new Random().nextInt(99));
                        map.put("provider_province", "MY PROV"+new Random().nextInt(99));

                        Odkate.launchODKForm(getItem(i).toString().replace(".xml", ""), "", activity, map);
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
            return convertView;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.v(getClass().getName(), "REQ C:"+requestCode+" - RES C:"+requestCode+" - DATA:"+data);
        if (requestCode == Odkate.ODK_FORM_ENTRY_REQUEST_CODE){
            if (resultCode == RESULT_OK){
                Cursor instanceCursor = null;
                try {
                    instanceCursor = getContentResolver().query(data.getData(), null, null, null, null);
                    if (instanceCursor.getCount() != 1) {
                        Log.v(getClass().getName(), getString(org.odk.collect.android.R.string.bad_uri, data.getData()));
                        return;
                    } else {
                        instanceCursor.moveToFirst();
                        String instancePath = instanceCursor.getString(instanceCursor
                                .getColumnIndex(InstanceProviderAPI.InstanceColumns.INSTANCE_FILE_PATH));
                        File instance = new File(instancePath);
                        // convert files into a byte array
                        byte[] fileBytes = FileUtils.getFileAsBytes(instance);
                        Log.v(getClass().getName(), new String(fileBytes));
                        // do something with data
                        String submission = new FormSubmissionUtils(activity).generateFormSubmisionFromXMLString(
                                new String[]{}, data.getData()).toString().replace("\\/", "/");
                        Log.v(getClass().getName(), submission);
                        FileWriter f = new FileWriter(new File(Collect.INSTANCES_PATH+"/submission"+ DateTime.now().toString("yyyy-MM-ddHHmmss")+".json"), false);
                        f.write(submission);
                        f.flush();
                        f.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (instanceCursor != null) {
                        instanceCursor.close();
                    }
                }
            }
            else {
                //todo show error
            }
        }
    }
}
