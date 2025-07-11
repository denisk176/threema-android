/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import org.slf4j.Logger;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import ch.threema.app.BuildConfig;
import ch.threema.app.R;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.dialogs.ShowOnceDialog;
import ch.threema.app.services.GroupService;
import ch.threema.app.restrictions.AppRestrictionUtil;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.LogUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.GroupModel;

import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

public class GroupAddActivity extends MemberChooseActivity implements GenericAlertDialog.DialogClickListener {
    private static final Logger logger = LoggingUtil.getThreemaLogger("GroupAddActivity");

    private static final String BUNDLE_EXISTING_MEMBERS = "ExMem";
    private static final String DIALOG_TAG_NO_MEMBERS = "NoMem";
    private static final String DIALOG_TAG_NOTE_GROUP_HOWTO = "note_group_hint";

    private GroupService groupService;
    private GroupModel groupModel;
    private boolean appendMembers;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);
    }

    @Override
    protected boolean initActivity(Bundle savedInstanceState) {
        if (!super.initActivity(savedInstanceState)) {
            return false;
        }

        if (AppRestrictionUtil.isCreateGroupDisabled(this)) {
            Toast.makeText(this, R.string.disabled_by_policy_short, Toast.LENGTH_LONG).show();
            return false;
        }

        try {
            this.groupService = serviceManager.getGroupService();
        } catch (Exception e) {
            LogUtil.exception(e, this);
            return false;
        }

        initData(savedInstanceState);

        return true;
    }

    @Override
    @StringRes
    protected int getNotice() {
        return 0;
    }

    @Override
    protected int getMode() {
        return appendMembers ? MODE_ADD_TO_GROUP : MODE_NEW_GROUP;
    }

    @Override
    protected void initData(@Nullable Bundle savedInstanceState) {
        this.appendMembers = false;
        this.excludedIdentities = new ArrayList<>();
        try {
            int groupId = IntentDataUtil.getGroupId(this.getIntent());
            if (this.groupService != null && groupId > 0) {
                this.groupModel = this.groupService.getById(groupId);
                this.appendMembers = (this.groupModel != null && this.groupService.isGroupCreator(this.groupModel));
                String[] excluded = IntentDataUtil.getContactIdentities(this.getIntent());
                if (excluded != null && excluded.length > 0) {
                    this.excludedIdentities = new ArrayList<>(Arrays.asList(excluded));
                }
            }
        } catch (Exception e) {
            LogUtil.exception(e, this);
            return;
        }

        updateToolbarSubtitle(R.string.title_select_contacts);

        initList();

        if (!appendMembers && savedInstanceState == null) {
            ShowOnceDialog.newInstance(R.string.title_addgroup, R.string.note_group_howto, 0).show(getSupportFragmentManager(), DIALOG_TAG_NOTE_GROUP_HOWTO);
        }
    }

    @Override
    protected void menuNext(@NonNull final List<ContactModel> selectedContacts) {
        final int previousContacts = this.appendMembers ? excludedIdentities.size() : 1; // user counts as one contact

        if (!selectedContacts.isEmpty()) {
            if ((previousContacts + selectedContacts.size()) > BuildConfig.MAX_GROUP_SIZE) {
                Toast.makeText(this, String.format(getString(R.string.group_select_max), BuildConfig.MAX_GROUP_SIZE - previousContacts), Toast.LENGTH_LONG).show();
            } else {
                createOrUpdateGroup(selectedContacts);
            }
        } else if (groupModel != null) {
            // Adding group members to existing group (none selected)
            createOrUpdateGroup(Collections.emptyList());
        } else {
            // Adding group members to new group (none selected)
            GenericAlertDialog.newInstance(R.string.title_addgroup, R.string.group_create_no_members, R.string.yes, R.string.no, 0).show(getSupportFragmentManager(), DIALOG_TAG_NO_MEMBERS);
        }
    }

    private void createOrUpdateGroup(@NonNull final List<ContactModel> selectedContacts) {
        //ok!
        if (this.groupModel != null) {
            // edit group mode
            Intent intent = new Intent();
            IntentDataUtil.append(selectedContacts, intent);
            setResult(RESULT_OK, intent);
            finish();
        } else {
            // new group mode
            Intent nextIntent = new Intent(this, GroupAdd2Activity.class);
            IntentDataUtil.append(selectedContacts, nextIntent);
            startActivityForResult(nextIntent, ThreemaActivity.ACTIVITY_ID_GROUP_ADD);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ThreemaActivity.ACTIVITY_ID_GROUP_ADD) {
            if (resultCode != RESULT_CANCELED) {
                finish();
            }
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putStringArrayList(BUNDLE_EXISTING_MEMBERS, this.excludedIdentities);
    }

    @Override
    public void onYes(String tag, Object data) {
        createOrUpdateGroup(new ArrayList<>());
    }
}
