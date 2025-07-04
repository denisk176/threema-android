/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2025 Threema GmbH
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

package ch.threema.app.dialogs;

import android.app.Activity;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.slf4j.Logger;

import java.util.ArrayList;

import ch.threema.app.R;
import ch.threema.app.emojis.EmojiTextView;
import ch.threema.app.ui.SelectorDialogItem;
import ch.threema.base.utils.LoggingUtil;

import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

public class SelectorDialog extends ThreemaDialogFragment {
    private static final Logger logger = LoggingUtil.getThreemaLogger("SelectorDialog");
    private SelectorDialogClickListener callback;
    private SelectorDialogInlineClickListener inlineCallback;
    private Activity activity;

    private static final String BUNDLE_TITLE_EXTRA = "title";
    private static final String BUNDLE_ITEMS_EXTRA = "items";
    private static final String BUNDLE_TAGS_EXTRA = "tags";
    private static final String BUNDLE_NEGATIVE_EXTRA = "negative";
    private static final String BUNDLE_LISTENER_EXTRA = "listener";

    public static SelectorDialog newInstance(String title, ArrayList<SelectorDialogItem> items, String negative) {
        SelectorDialog dialog = new SelectorDialog();
        Bundle args = new Bundle();
        args.putString(BUNDLE_TITLE_EXTRA, title);
        args.putSerializable(BUNDLE_ITEMS_EXTRA, items);
        args.putString(BUNDLE_NEGATIVE_EXTRA, negative);

        dialog.setArguments(args);
        return dialog;
    }

    public static SelectorDialog newInstance(String title, ArrayList<SelectorDialogItem> items, ArrayList<Integer> tags, String negative) {
        SelectorDialog dialog = new SelectorDialog();
        Bundle args = new Bundle();
        args.putString(BUNDLE_TITLE_EXTRA, title);
        args.putIntegerArrayList(BUNDLE_TAGS_EXTRA, tags);
        args.putSerializable(BUNDLE_ITEMS_EXTRA, items);
        args.putString(BUNDLE_NEGATIVE_EXTRA, negative);

        dialog.setArguments(args);
        return dialog;
    }

    public static SelectorDialog newInstance(String title, ArrayList<SelectorDialogItem> items, String negative, SelectorDialogInlineClickListener listener) {
        SelectorDialog dialog = new SelectorDialog();
        Bundle args = new Bundle();
        args.putString(BUNDLE_TITLE_EXTRA, title);
        args.putSerializable(BUNDLE_ITEMS_EXTRA, items);
        args.putString(BUNDLE_NEGATIVE_EXTRA, negative);
        args.putParcelable(BUNDLE_LISTENER_EXTRA, listener);

        dialog.setArguments(args);
        return dialog;
    }

    public interface SelectorDialogClickListener {
        void onClick(String tag, int which, @Nullable Object data);

        default void onCancel(String tag) {
        }

        default void onNo(String tag) {
        }
    }

    public interface SelectorDialogInlineClickListener extends Parcelable {
        void onClick(String tag, int which, Object data);
    }

    @Override
    public void onAttach(@NonNull Activity activity) {
        super.onAttach(activity);

        this.activity = activity;
    }

    @Override
    public void onCancel(@NonNull DialogInterface dialogInterface) {
        super.onCancel(dialogInterface);

        if (inlineCallback == null) {
            callback.onCancel(this.getTag());
        }
    }

    @NonNull
    @Override
    public AppCompatDialog onCreateDialog(Bundle savedInstanceState) {
        Bundle arguments = getArguments();
        String title = arguments.getString(BUNDLE_TITLE_EXTRA);
        final ArrayList<SelectorDialogItem> items = (ArrayList<SelectorDialogItem>) arguments.getSerializable(BUNDLE_ITEMS_EXTRA);
        final ArrayList<Integer> tags = arguments.getIntegerArrayList(BUNDLE_TAGS_EXTRA);
        String negative = arguments.getString(BUNDLE_NEGATIVE_EXTRA);
        SelectorDialogInlineClickListener listener = arguments.getParcelable(BUNDLE_LISTENER_EXTRA);

        if (listener != null) {
            inlineCallback = listener;
        }

        final String fragmentTag = this.getTag();

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getActivity(), getTheme());
        if (title != null) {
            EmojiTextView emojiTextView = new EmojiTextView(new ContextThemeWrapper(getContext(), R.style.MaterialAlertDialog_Material3_Title_Text));
            emojiTextView.setText(title);
            emojiTextView.setSingleLine(false);
            emojiTextView.setMaxLines(2);
            int padding = getResources().getDimensionPixelSize(R.dimen.edittext_padding);
            int paddingRight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
            emojiTextView.setPadding(padding, padding, paddingRight, 0);
            builder.setCustomTitle(emojiTextView);
        }

        ListAdapter adapter = new ArrayAdapter<>(
            activity,
            R.layout.item_selector_dialog,
            R.id.text1,
            items) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                //Use super class to create the View
                View v = super.getView(position, convertView, parent);
                TextView selectorOptionDesc = v.findViewById(R.id.text1);

                //Put the image on the TextView
                selectorOptionDesc.setCompoundDrawablesWithIntrinsicBounds(items.get(position).getIcon(), 0, 0, 0);

                //Add margin between image and text (support various screen densities)
                selectorOptionDesc.setCompoundDrawablePadding(getResources().getDimensionPixelSize(R.dimen.listitem_standard_margin_left_right));

                return v;
            }
        };

        builder.setAdapter(adapter, (dialog, which) -> {
            dialog.dismiss();

            if (tags != null && !tags.isEmpty()) {
                if (inlineCallback != null) {
                    inlineCallback.onClick(fragmentTag, tags.get(which), object);
                } else {
                    callback.onClick(fragmentTag, tags.get(which), object);
                }
            } else {
                if (inlineCallback != null) {
                    inlineCallback.onClick(fragmentTag, which, object);
                } else {
                    callback.onClick(fragmentTag, which, object);
                }
            }
        });

        if (negative != null) {
            builder.setNegativeButton(negative, (dialog, which) -> {
                dialog.dismiss();
                if (inlineCallback == null) {
                    callback.onNo(fragmentTag);
                }
            });
        }

        return builder.create();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);

        try {
            callback = (SelectorDialogClickListener) getTargetFragment();
        } catch (ClassCastException e) {
            //
        }

        // maybe called from an activity rather than a fragment
        if (callback == null) {
            if ((activity instanceof SelectorDialogClickListener)) {
                callback = (SelectorDialogClickListener) activity;
            }
        }
    }
}
