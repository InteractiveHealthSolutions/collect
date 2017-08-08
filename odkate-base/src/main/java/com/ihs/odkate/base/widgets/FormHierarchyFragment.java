package com.ihs.odkate.base.widgets;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.ScrollView;

import com.ihs.odkate.base.R;
import com.ihs.odkate.base.utils.OdkateUtils;

import org.javarosa.core.model.FormIndex;
import org.javarosa.form.api.FormEntryCaption;
import org.javarosa.form.api.FormEntryController;
import org.javarosa.form.api.FormEntryPrompt;
import org.odk.collect.android.activities.FormEntryActivity;
import org.odk.collect.android.adapters.HierarchyListAdapter;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.exception.JavaRosaException;
import org.odk.collect.android.logic.FormController;
import org.odk.collect.android.logic.HierarchyElement;
import org.odk.collect.android.utilities.ApplicationConstants;
import org.odk.collect.android.utilities.FormEntryPromptUtils;

import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

import static android.app.Activity.RESULT_OK;

/**
 * Created by Maimoona on 8/1/2017.
 */

public class FormHierarchyFragment extends Fragment implements AdapterView.OnItemClickListener {
    private static final int CHILD = 1;
    private static final int EXPANDED = 2;
    private static final int COLLAPSED = 3;
    private static final int QUESTION = 4;

    private static final String mIndent = "     ";
    private Activity context;
    private ListView listView;
    private View rootView;
    List<HierarchyElement> formList = new ArrayList<>();

    public FormHierarchyFragment(){
        this.context = getActivity();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.form_hierarchy_list_embedable, container, false);
        return rootView;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        listView = (ListView) rootView.findViewById(R.id.list);
        listView.setOnItemClickListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.v(getClass().getName(), "HHH:  onResume");

        context = getActivity();

        ((LinearLayout)context.findViewById(R.id.questionholder)).setOnHierarchyChangeListener(
                new ViewGroup.OnHierarchyChangeListener() {
            @Override
            public void onChildViewAdded(View view, View view1) {
                formList.clear();
                listView.setAdapter(null);

                hierarchyView();
            }

            @Override
            public void onChildViewRemoved(View view, View view1) {}
        });
    }

    public void hierarchyView() {
        try {
            FormController formController = Collect.getInstance().getFormController();
            // Record the current index so we can return to the same place if the user hits 'back'.
            FormIndex currentIndex = formController.getFormIndex();

            FormIndex startTest = FormIndex.createBeginningOfFormIndex();

            formController.jumpToIndex(startTest);

            // If we're not at the first level, we're inside a repeated group so we want to only
            // display everything enclosed within that group.
            String contextGroupRef = "";

            // If we're currently at a repeat node, record the name of the node and step to the next
            // node to display.
/*            if (formController.getEvent() == FormEntryController.EVENT_REPEAT) {
                contextGroupRef = formController.getFormIndex().getReference().toString(true);
                formController.stepToNextEvent(FormController.STEP_INTO_GROUP);
            }
            else {
                FormIndex startTest = formController.stepIndexOut(currentIndex);
                // If we have a 'group' tag, we want to step back until we hit a repeat or the
                // beginning.
                while (startTest != null
                        && formController.getEvent(startTest) == FormEntryController.EVENT_GROUP) {
                    startTest = formController.stepIndexOut(startTest);
                }
                if (startTest == null) {
                    // check to see if the question is at the first level of the hierarchy. If it
                    // is, display the root level from the beginning.
                    formController.jumpToIndex(FormIndex.createBeginningOfFormIndex());
                }
                else {
                    // otherwise we're at a repeated group
                    formController.jumpToIndex(startTest);
                }

                // now test again for repeat. This should be true at this point or we're at the
                // beginning
                if (formController.getEvent() == FormEntryController.EVENT_REPEAT) {
                    contextGroupRef = formController.getFormIndex().getReference().toString(true);
                    formController.stepToNextEvent(FormController.STEP_INTO_GROUP);
                }
            }*/

            int event = formController.getEvent();
            if (event == FormEntryController.EVENT_BEGINNING_OF_FORM) {
                // The beginning of form has no valid prompt to display.
                formController.stepToNextEvent(FormController.STEP_INTO_GROUP);
                contextGroupRef = formController.getFormIndex().getReference().getParentRef().toString(true);
                //todo path.setVisibility(View.GONE);
                //todo jumpPreviousButton.setEnabled(false);
            }
            else {
                /*todo path.setVisibility(View.VISIBLE);
                path.setText(getCurrentPath());
                jumpPreviousButton.setEnabled(true);*/
            }

            // Refresh the current event in case we did step forward.
            event = formController.getEvent();

            // Big change from prior implementation:
            //
            // The ref strings now include the instance number designations
            // i.e., [0], [1], etc. of the repeat groups (and also [1] for
            // non-repeat elements).
            //
            // The contextGroupRef is now also valid for the top-level form.
            //
            // The repeatGroupRef is null if we are not skipping a repeat
            // section.
            //
            String repeatGroupRef = null;

            event_search:
            while (event != FormEntryController.EVENT_END_OF_FORM) {
                FormIndex tempIndex = formController.getFormIndex();
                if (tempIndex.equals(currentIndex)){
                    break;
                }

                // get the ref to this element
                String currentRef = formController.getFormIndex().getReference().toString(true);

                // retrieve the current group
                String curGroup = (repeatGroupRef == null) ? contextGroupRef : repeatGroupRef;

                if (!currentRef.startsWith(curGroup)) {
                    // We have left the current group
                    if (repeatGroupRef == null) {
                        // We are done.
                        break event_search;
                    } else {
                        // exit the inner repeat group
                        repeatGroupRef = null;
                    }
                }

                if (repeatGroupRef != null) {
                    // We're in a repeat group within the one we want to list
                    // skip this question/group/repeat and move to the next index.
                    contextGroupRef = repeatGroupRef;
                    //event = formController.stepToNextEvent(FormController.STEP_INTO_GROUP);
                    //todo continue;
                }


                switch (event) {
                    case FormEntryController.EVENT_QUESTION:

                        FormEntryPrompt fp = formController.getQuestionPrompt();
                        String label = fp.getLongText();
                        if (!fp.isReadOnly() || (label != null && label.length() > 0)) {
                            // show the question if it is an editable field.
                            // or if it is read-only and the label is not blank.
                            String answerDisplay = FormEntryPromptUtils.getAnswerText(fp);
                            formList.add(
                                    new HierarchyElement(fp.getLongText(), answerDisplay, null,
                                            Color.WHITE, QUESTION, fp.getIndex()));
                        }
                        break;
                    case FormEntryController.EVENT_GROUP:
                        // ignore group events
                        break;
                    case FormEntryController.EVENT_PROMPT_NEW_REPEAT:
                        // this would display the 'add new repeat' dialog
                        // ignore it.
                        break;
                    case FormEntryController.EVENT_REPEAT:
                        FormEntryCaption fc = formController.getCaptionPrompt();
                        // push this repeat onto the stack.
                        repeatGroupRef = currentRef;
                        // Because of the guard conditions above, we will skip
                        // everything until we exit this repeat.
                        //
                        // Note that currentRef includes the multiplicity of the
                        // repeat (e.g., [0], [1], ...), so every repeat will be
                        // detected as different and reach this case statement.
                        // Only the [0] emits the repeat header.
                        // Every one displays the descend-into action element.

                        //if (fc.getMultiplicity() == 0) {
                            // Display the repeat header for the group.
                            HierarchyElement group =
                                    new HierarchyElement(fc.getLongText()+ " " + (fc.getMultiplicity() + 1),
                                            null, ContextCompat
                                            .getDrawable(context, org.odk.collect.android.R.drawable.expander_ic_maximized),
                                            Color.LTGRAY,
                                            EXPANDED, fc.getIndex());
                            formList.add(group);
                       // }
                        // Add this group name to the drop down list for this repeating group.
                        /*HierarchyElement h = formList.get(formList.size() - 1);
                        h.addChild(new HierarchyElement(mIndent + fc.getLongText() + " "
                                + (fc.getMultiplicity() + 1), null, null, Color.MAGENTA, CHILD, fc
                                .getIndex()));*/
                        break;
                }
                event = formController.stepToNextEvent(FormController.STEP_INTO_GROUP);
            }

            HierarchyListAdapter itla = new HierarchyListAdapter(context);
            itla.setListItems(formList);
            listView.setAdapter(itla);

            // set the controller back to the current index in case the user hits 'back'
            formController.jumpToIndex(currentIndex);
        } catch (Exception e) {
            Timber.e(e);
            e.printStackTrace();
 //           OdkateUtils.showErrorDialog(context, e.getMessage());
        }

        getActivity().findViewById(R.id.main_scroll).post(new Runnable() {

            @Override
            public void run() {
                ((ScrollView)getActivity().findViewById(R.id.main_scroll)).fullScroll(ScrollView.FOCUS_DOWN);
            }
        });
    }

    private void goUpLevel() {
        Collect.getInstance().getFormController().stepToOuterScreenEvent();

        hierarchyView();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        HierarchyElement h = (HierarchyElement) listView.getItemAtPosition(position);
        FormIndex index = h.getFormIndex();
        if (index == null) {
            goUpLevel();
            return;
        }

        String nodeRef = h.getFormIndex().getReference().toString(true);

        switch (h.getType()) {
            /*case EXPANDED:
                Collect.getInstance().getActivityLogger().logInstanceAction(this, "onListItemClick",
                        "COLLAPSED", h.getFormIndex());
                h.setType(COLLAPSED);

                for (int i = position+1; i < formList.size(); i++) {
                    HierarchyElement temp = formList.get(i);
                    String tempref = temp.getFormIndex().getReference().toString(true);
                    if (tempref.contains(nodeRef)){
                        RelativeLayout ch = (RelativeLayout) listView.getChildAt(i);
                        ch.setVisibility(View.GONE);
                        ch.setLayoutParams(new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0));
                    }
                    else {
                        break;
                    }
                }
                h.setIcon(ContextCompat.getDrawable(context, org.odk.collect.android.R.drawable.expander_ic_minimized));
                break;
            case COLLAPSED:
                Collect.getInstance().getActivityLogger().logInstanceAction(this, "onListItemClick",
                        "EXPANDED", h.getFormIndex());
                h.setType(EXPANDED);
                for (int i = position+1; i < formList.size(); i++) {
                    HierarchyElement temp = formList.get(i);
                    String tempref = temp.getFormIndex().getReference().toString(true);
                    if (tempref.contains(nodeRef)){
                        listView.getChildAt(i).setVisibility(View.VISIBLE);
                    }
                    else {
                        break;
                    }
                }
                h.setIcon(ContextCompat.getDrawable(context, org.odk.collect.android.R.drawable.expander_ic_maximized));
                break;*/
            case QUESTION:
                Collect.getInstance().getActivityLogger().logInstanceAction(this, "onListItemClick",
                        "QUESTION-JUMP", index);
                Collect.getInstance().getFormController().jumpToIndex(index);
                ((FormEntryActivity)getActivity()).refreshCurrentView();
//                if (Collect.getInstance().getFormController().indexIsInFieldList()) {
//                    try {
//                        Collect.getInstance().getFormController().stepToPreviousScreenEvent();
//                    } catch (JavaRosaException e) {
//                        Timber.e(e);
//                        OdkateUtils.showErrorDialog(context, e.getMessage());
//                        return;
//                    }
//                }
//                context.setResult(RESULT_OK);
                /*String formMode = context.getIntent().getStringExtra(ApplicationConstants.BundleKeys.FORM_MODE);
                if (formMode == null || ApplicationConstants.FormModes.EDIT_SAVED.equalsIgnoreCase(formMode)) {
                    context.finish();
                }*/
                return;
            /*case CHILD:
                Collect.getInstance().getActivityLogger().logInstanceAction(this, "onListItemClick",
                        "REPEAT-JUMP", h.getFormIndex());
                Collect.getInstance().getFormController().jumpToIndex(h.getFormIndex());
                context.setResult(RESULT_OK);
                hierarchyView();
                return;*/
        }

        // Should only get here if we've expanded or collapsed a group
        HierarchyListAdapter itla = new HierarchyListAdapter(context);
        itla.setListItems(formList);
        listView.setAdapter(itla);
        listView.setSelection(position);
    }
}