package org.fdroid.fdroid.views;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.TextViewCompat;
import android.support.v7.text.AllCapsTransformationMethod;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.assist.ImageScaleType;

import org.fdroid.fdroid.Preferences;
import org.fdroid.fdroid.R;
import org.fdroid.fdroid.Utils;
import org.fdroid.fdroid.data.Apk;
import org.fdroid.fdroid.data.ApkProvider;
import org.fdroid.fdroid.data.App;
import org.fdroid.fdroid.data.InstalledAppProvider;
import org.fdroid.fdroid.data.RepoProvider;
import org.fdroid.fdroid.privileged.views.AppDiff;
import org.fdroid.fdroid.privileged.views.AppSecurityPermissions;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

public class AppDetailsRecyclerViewAdapter
        extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface AppDetailsRecyclerViewAdapterCallbacks {

        boolean isAppDownloading();

        void enableAndroidBeam();

        void disableAndroidBeam();

        void openUrl(String url);

        void installApk();

        void installApk(Apk apk);

        void upgradeApk();

        void uninstallApk();

        void installCancel();

        void launchApk();

    }

    private static final int VIEWTYPE_HEADER = 0;
    private static final int VIEWTYPE_SCREENSHOTS = 1;
    private static final int VIEWTYPE_WHATS_NEW = 2;
    private static final int VIEWTYPE_DONATE = 3;
    private static final int VIEWTYPE_LINKS = 4;
    private static final int VIEWTYPE_PERMISSIONS = 5;
    private static final int VIEWTYPE_VERSIONS = 6;
    private static final int VIEWTYPE_VERSION = 7;

    private final Context context;
    @NonNull
    private App app;
    private final AppDetailsRecyclerViewAdapterCallbacks callbacks;
    private RecyclerView recyclerView;
    private ArrayList<Object> items;
    private ArrayList<Apk> versions;
    private boolean showVersions;

    private HeaderViewHolder headerView;

    public AppDetailsRecyclerViewAdapter(Context context, @NonNull App app, AppDetailsRecyclerViewAdapterCallbacks callbacks) {
        this.context = context;
        this.callbacks = callbacks;
        this.app = app;
        updateItems(app);
    }

    public void updateItems(@NonNull App app) {
        this.app = app;

        // Get versions
        versions = new ArrayList<>();
        final List<Apk> apks = ApkProvider.Helper.findByPackageName(context, this.app.packageName);
        for (final Apk apk : apks) {
            if (apk.compatible || Preferences.get().showIncompatibleVersions()) {
                versions.add(apk);
            }
        }

        if (items == null) {
            items = new ArrayList<>();
        } else {
            items.clear();
        }
        addItem(VIEWTYPE_HEADER);
        addItem(VIEWTYPE_SCREENSHOTS);
        addItem(VIEWTYPE_WHATS_NEW);
        addItem(VIEWTYPE_DONATE);
        addItem(VIEWTYPE_LINKS);
        addItem(VIEWTYPE_PERMISSIONS);
        addItem(VIEWTYPE_VERSIONS);

        notifyDataSetChanged();
    }

    private void setShowVersions(boolean showVersions) {
        this.showVersions = showVersions;
        items.removeAll(versions);
        if (showVersions) {
            items.addAll(items.indexOf(VIEWTYPE_VERSIONS) + 1, versions);
        }
        notifyDataSetChanged();
    }

    private void addItem(int item) {
        // Gives us a chance to hide sections that are not used, e.g. the donate section when
        // we have no donation links.
        if (item == VIEWTYPE_DONATE && !shouldShowDonate()) {
            return;
        } else if (item == VIEWTYPE_PERMISSIONS && !shouldShowPermissions()) {
            return;
        }
        items.add(item);
    }

    private boolean shouldShowPermissions() {
        // Figure out if we should show permissions section
        Apk curApk = null;
        for (int i = 0; i < versions.size(); i++) {
            final Apk apk = versions.get(i);
            if (apk.versionCode == app.suggestedVersionCode) {
                curApk = apk;
                break;
            }
        }
        final boolean curApkCompatible = curApk != null && curApk.compatible;
        return versions.size() > 0 && (curApkCompatible || Preferences.get().showIncompatibleVersions());
    }

    private boolean shouldShowDonate() {
        return uriIsSetAndCanBeOpened(app.donateURL) ||
                uriIsSetAndCanBeOpened(app.getBitcoinUri()) ||
                uriIsSetAndCanBeOpened(app.getLitecoinUri()) ||
                uriIsSetAndCanBeOpened(app.getFlattrUri());
    }

    public void clearProgress() {
        setProgress(0, 0, 0);
    }

    public void setProgress(int bytesDownloaded, int totalBytes, int resIdString) {
        if (headerView != null) {
            headerView.setProgress(bytesDownloaded, totalBytes, resIdString);
        }
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case VIEWTYPE_HEADER:
                View header = inflater.inflate(R.layout.app_details2_header, parent, false);
                return new HeaderViewHolder(header);
            case VIEWTYPE_SCREENSHOTS:
                View screenshots = inflater.inflate(R.layout.app_details2_screenshots, parent, false);
                return new ScreenShotsViewHolder(screenshots);
            case VIEWTYPE_WHATS_NEW:
                View whatsNew = inflater.inflate(R.layout.app_details2_whatsnew, parent, false);
                return new WhatsNewViewHolder(whatsNew);
            case VIEWTYPE_DONATE:
                View donate = inflater.inflate(R.layout.app_details2_donate, parent, false);
                return new DonateViewHolder(donate);
            case VIEWTYPE_LINKS:
                View links = inflater.inflate(R.layout.app_details2_links, parent, false);
                return new LinksViewHolder(links);
            case VIEWTYPE_PERMISSIONS:
                View permissions = inflater.inflate(R.layout.app_details2_links, parent, false);
                return new PermissionsViewHolder(permissions);
            case VIEWTYPE_VERSIONS:
                View versions = inflater.inflate(R.layout.app_details2_links, parent, false);
                return new VersionsViewHolder(versions);
            case VIEWTYPE_VERSION:
                View version = inflater.inflate(R.layout.apklistitem, parent, false);
                return new VersionViewHolder(version);
        }
        return null;
    }

    @Override
    public void onBindViewHolder(final RecyclerView.ViewHolder holder, int position) {
        int viewType = getItemViewType(position);
        switch (viewType) {
            case VIEWTYPE_HEADER:
                HeaderViewHolder header = (HeaderViewHolder) holder;
                headerView = header;
                header.bindModel();
                break;
            case VIEWTYPE_SCREENSHOTS:
                ((ScreenShotsViewHolder) holder).bindModel();
                break;
            case VIEWTYPE_WHATS_NEW:
                ((WhatsNewViewHolder) holder).bindModel();
                break;
            case VIEWTYPE_DONATE:
                ((DonateViewHolder) holder).bindModel();
                break;
            case VIEWTYPE_LINKS:
                ((LinksViewHolder) holder).bindModel();
                break;
            case VIEWTYPE_PERMISSIONS:
                ((PermissionsViewHolder) holder).bindModel();
                break;
            case VIEWTYPE_VERSIONS:
                ((VersionsViewHolder) holder).bindModel();
                break;
            case VIEWTYPE_VERSION:
                final Apk apk = (Apk) items.get(position);
                ((VersionViewHolder) holder).bindModel(apk);
                break;
        }
    }

    @Override
    public void onViewRecycled(RecyclerView.ViewHolder holder) {
        if (holder instanceof HeaderViewHolder) {
            headerView = null;
        }
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public int getItemViewType(int position) {
        if (items.get(position) instanceof Apk) {
            return VIEWTYPE_VERSION;
        }
        return (Integer) items.get(position);
    }

    public class HeaderViewHolder extends RecyclerView.ViewHolder {
        private static final int MAX_LINES = 5;

        final ImageView iconView;
        final TextView titleView;
        final TextView authorView;
        final TextView summaryView;
        final TextView descriptionView;
        final TextView descriptionMoreView;
        final View buttonLayout;
        final Button buttonPrimaryView;
        final Button buttonSecondaryView;
        final View progressLayout;
        final ProgressBar progressBar;
        final TextView progressLabel;
        final TextView progressPercent;
        final View progressCancel;
        final DisplayImageOptions displayImageOptions;

        HeaderViewHolder(View view) {
            super(view);
            iconView = (ImageView) view.findViewById(R.id.icon);
            titleView = (TextView) view.findViewById(R.id.title);
            authorView = (TextView) view.findViewById(R.id.author);
            summaryView = (TextView) view.findViewById(R.id.summary);
            descriptionView = (TextView) view.findViewById(R.id.description);
            descriptionMoreView = (TextView) view.findViewById(R.id.description_more);
            buttonLayout = view.findViewById(R.id.button_layout);
            buttonPrimaryView = (Button) view.findViewById(R.id.primaryButtonView);
            buttonSecondaryView = (Button) view.findViewById(R.id.secondaryButtonView);
            progressLayout = view.findViewById(R.id.progress_layout);
            progressBar = (ProgressBar) view.findViewById(R.id.progress_bar);
            progressLabel = (TextView) view.findViewById(R.id.progress_label);
            progressPercent = (TextView) view.findViewById(R.id.progress_percent);
            progressCancel = view.findViewById(R.id.progress_cancel);
            displayImageOptions = new DisplayImageOptions.Builder()
                    .cacheInMemory(true)
                    .cacheOnDisk(true)
                    .imageScaleType(ImageScaleType.NONE)
                    .showImageOnLoading(R.drawable.ic_repo_app_default)
                    .showImageForEmptyUri(R.drawable.ic_repo_app_default)
                    .bitmapConfig(Bitmap.Config.RGB_565)
                    .build();
            descriptionView.setMaxLines(MAX_LINES);
            descriptionView.setEllipsize(TextUtils.TruncateAt.MARQUEE);
            descriptionMoreView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Make this "header section" the focused child, so that RecyclerView will use
                    // it as the anchor in the layout process. Otherwise the RV might select another
                    // view as the anchor, resulting in that the top of this view is instead scrolled
                    // off the screen. Refer to LinearLayoutManager.updateAnchorFromChildren(...).
                    recyclerView.requestChildFocus(itemView, itemView);
                    if (TextViewCompat.getMaxLines(descriptionView) != MAX_LINES) {
                        descriptionView.setMaxLines(MAX_LINES);
                        descriptionMoreView.setText(R.string.more);
                    } else {
                        descriptionView.setMaxLines(Integer.MAX_VALUE);
                        descriptionMoreView.setText(R.string.less);
                    }
                }
            });
            // Set ALL caps (in a way compatible with SDK 10)
            AllCapsTransformationMethod allCapsTransformation = new AllCapsTransformationMethod(view.getContext());
            buttonPrimaryView.setTransformationMethod(allCapsTransformation);
            buttonSecondaryView.setTransformationMethod(allCapsTransformation);
            descriptionMoreView.setTransformationMethod(allCapsTransformation);
        }

        public void setProgress(int bytesDownloaded, int totalBytes, int resIdString) {
            if (bytesDownloaded == 0 && totalBytes == 0) {
                // Remove progress bar
                progressLayout.setVisibility(View.GONE);
                buttonLayout.setVisibility(View.VISIBLE);
            } else {
                progressBar.setMax(totalBytes);
                progressBar.setProgress(bytesDownloaded);
                progressBar.setIndeterminate(totalBytes == -1);
                if (resIdString != 0) {
                    progressLabel.setText(resIdString);
                    progressPercent.setText("");
                } else if (totalBytes > 0 && bytesDownloaded >= 0) {
                    float percent = bytesDownloaded * 100 / totalBytes;
                    progressLabel.setText(Utils.getFriendlySize(bytesDownloaded) + " / " + Utils.getFriendlySize(totalBytes));
                    NumberFormat format = NumberFormat.getPercentInstance();
                    format.setMaximumFractionDigits(0);
                    progressPercent.setText(format.format(percent / 100));
                } else if (bytesDownloaded >= 0) {
                    progressLabel.setText(Utils.getFriendlySize(bytesDownloaded));
                    progressPercent.setText("");
                }

                // Make sure it's visible
                if (progressLayout.getVisibility() != View.VISIBLE) {
                    progressLayout.setVisibility(View.VISIBLE);
                    buttonLayout.setVisibility(View.GONE);
                }
            }
        }

        public void bindModel() {
            ImageLoader.getInstance().displayImage(app.iconUrlLarge, iconView, displayImageOptions);
            titleView.setText(app.name);
            if (!TextUtils.isEmpty(app.author)) {
                authorView.setText(context.getString(R.string.by_author) + " " + app.author);
                authorView.setVisibility(View.VISIBLE);
            } else {
                authorView.setVisibility(View.GONE);
            }
            summaryView.setText(app.summary);
            final Spanned desc = Html.fromHtml(app.description, null, new Utils.HtmlTagHandler());
            descriptionView.setMovementMethod(LinkMovementMethod.getInstance());
            descriptionView.setText(trimTrailingNewlines(desc));
            if (descriptionView.getText() instanceof Spannable) {
                Spannable spannable = (Spannable) descriptionView.getText();
                URLSpan[] spans = spannable.getSpans(0, spannable.length(), URLSpan.class);
                for (URLSpan span : spans) {
                    int start = spannable.getSpanStart(span);
                    int end = spannable.getSpanEnd(span);
                    int flags = spannable.getSpanFlags(span);
                    spannable.removeSpan(span);
                    // Create out own safe link span
                    SafeURLSpan safeUrlSpan = new SafeURLSpan(span.getURL());
                    spannable.setSpan(safeUrlSpan, start, end, flags);
                }
            }
            descriptionView.post(new Runnable() {
                @Override
                public void run() {
                    if (descriptionView.getLineCount() < HeaderViewHolder.MAX_LINES) {
                        descriptionMoreView.setVisibility(View.GONE);
                    } else {
                        descriptionMoreView.setVisibility(View.VISIBLE);
                    }
                }
            });
            buttonSecondaryView.setText(R.string.menu_uninstall);
            buttonSecondaryView.setVisibility(app.isInstalled() ? View.VISIBLE : View.INVISIBLE);
            buttonSecondaryView.setOnClickListener(onUnInstallClickListener);
            buttonPrimaryView.setText(R.string.menu_install);
            buttonPrimaryView.setVisibility(versions.size() > 0 ? View.VISIBLE : View.GONE);
            if (callbacks.isAppDownloading()) {
                buttonPrimaryView.setText(R.string.downloading);
                buttonPrimaryView.setEnabled(false);
            } else if (!app.isInstalled() && app.suggestedVersionCode > 0 && versions.size() > 0) {
                // Check count > 0 due to incompatible apps resulting in an empty list.
                callbacks.disableAndroidBeam();
                // Set Install button and hide second button
                buttonPrimaryView.setText(R.string.menu_install);
                buttonPrimaryView.setOnClickListener(onInstallClickListener);
                buttonPrimaryView.setEnabled(true);
            } else if (app.isInstalled()) {
                callbacks.enableAndroidBeam();
                if (app.canAndWantToUpdate(context)) {
                    buttonPrimaryView.setText(R.string.menu_upgrade);
                    buttonPrimaryView.setOnClickListener(onUpgradeClickListener);
                } else {
                    if (context.getPackageManager().getLaunchIntentForPackage(app.packageName) != null) {
                        buttonPrimaryView.setText(R.string.menu_launch);
                        buttonPrimaryView.setOnClickListener(onLaunchClickListener);
                    } else {
                        buttonPrimaryView.setVisibility(View.GONE);
                    }
                }
                buttonPrimaryView.setEnabled(true);
            }
            if (callbacks.isAppDownloading()) {
                buttonLayout.setVisibility(View.GONE);
                progressLayout.setVisibility(View.VISIBLE);
            } else {
                buttonLayout.setVisibility(View.VISIBLE);
                progressLayout.setVisibility(View.GONE);
            }
            progressCancel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    callbacks.installCancel();
                }
            });

        }
    }

    @Override
    public void onAttachedToRecyclerView(RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        this.recyclerView = recyclerView;
    }

    @Override
    public void onDetachedFromRecyclerView(RecyclerView recyclerView) {
        this.recyclerView = null;
        super.onDetachedFromRecyclerView(recyclerView);
    }

    private class ScreenShotsViewHolder extends RecyclerView.ViewHolder {
        final RecyclerView recyclerView;
        LinearLayoutManagerSnapHelper snapHelper;

        ScreenShotsViewHolder(View view) {
            super(view);
            recyclerView = (RecyclerView) view.findViewById(R.id.screenshots);
        }

        public void bindModel() {
            LinearLayoutManager lm = new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false);
            recyclerView.setLayoutManager(lm);
            ScreenShotsRecyclerViewAdapter adapter = new ScreenShotsRecyclerViewAdapter(itemView.getContext(), app);
            recyclerView.setAdapter(adapter);
            recyclerView.setHasFixedSize(true);
            recyclerView.setNestedScrollingEnabled(false);
            if (snapHelper != null) {
                snapHelper.attachToRecyclerView(null);
            }
            snapHelper = new LinearLayoutManagerSnapHelper(lm);
            snapHelper.setLinearSnapHelperListener(adapter);
            snapHelper.attachToRecyclerView(recyclerView);
        }
    }

    private class WhatsNewViewHolder extends RecyclerView.ViewHolder {
        final TextView textView;

        WhatsNewViewHolder(View view) {
            super(view);
            textView = (TextView) view.findViewById(R.id.text);
        }

        public void bindModel() {
            textView.setText("WHATS NEW GOES HERE");
        }
    }

    private class DonateViewHolder extends RecyclerView.ViewHolder {
        final TextView textView;
        final LinearLayout contentView;

        DonateViewHolder(View view) {
            super(view);
            textView = (TextView) view.findViewById(R.id.information);
            contentView = (LinearLayout) view.findViewById(R.id.ll_information);
        }

        public void bindModel() {
            contentView.removeAllViews();

            // Donate button
            if (uriIsSetAndCanBeOpened(app.donateURL)) {
                addLinkItemView(contentView, R.string.menu_donate, R.drawable.ic_donate, app.donateURL);
            }

            // Bitcoin
            if (uriIsSetAndCanBeOpened(app.getBitcoinUri())) {
                addLinkItemView(contentView, R.string.menu_bitcoin, R.drawable.ic_bitcoin, app.getBitcoinUri());
            }

            // Litecoin
            if (uriIsSetAndCanBeOpened(app.getLitecoinUri())) {
                addLinkItemView(contentView, R.string.menu_litecoin, R.drawable.ic_litecoin, app.getLitecoinUri());
            }

            // Flattr
            if (uriIsSetAndCanBeOpened(app.getFlattrUri())) {
                addLinkItemView(contentView, R.string.menu_flattr, R.drawable.ic_flattr, app.getFlattrUri());
            }
        }
    }

    private abstract class ExpandableLinearLayoutViewHolder extends RecyclerView.ViewHolder {
        final TextView headerView;
        final LinearLayout contentView;

        ExpandableLinearLayoutViewHolder(View view) {
            super(view);
            headerView = (TextView) view.findViewById(R.id.information);
            contentView = (LinearLayout) view.findViewById(R.id.ll_content);
        }
    }

    private class VersionsViewHolder extends ExpandableLinearLayoutViewHolder {

        VersionsViewHolder(View view) {
            super(view);
        }

        public void bindModel() {
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setShowVersions(!showVersions);
                }
            });
            headerView.setText(R.string.versions);
            TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(headerView, R.drawable.ic_access_time_24dp_grey600, 0, showVersions ? R.drawable.ic_expand_less_grey600 : R.drawable.ic_expand_more_grey600, 0);
        }
    }

    private class PermissionsViewHolder extends ExpandableLinearLayoutViewHolder {

        PermissionsViewHolder(View view) {
            super(view);
        }

        public void bindModel() {
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean shouldBeVisible = contentView.getVisibility() != View.VISIBLE;
                    contentView.setVisibility(shouldBeVisible ? View.VISIBLE : View.GONE);
                    TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(headerView, R.drawable.ic_lock_24dp_grey600, 0, shouldBeVisible ? R.drawable.ic_expand_less_grey600 : R.drawable.ic_expand_more_grey600, 0);
                }
            });
            headerView.setText(R.string.permissions);
            TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(headerView, R.drawable.ic_lock_24dp_grey600, 0, R.drawable.ic_expand_more_grey600, 0);
            contentView.removeAllViews();
            AppDiff appDiff = new AppDiff(context.getPackageManager(), versions.get(0));
            AppSecurityPermissions perms = new AppSecurityPermissions(context, appDiff.pkgInfo);
            contentView.addView(perms.getPermissionsView(AppSecurityPermissions.WHICH_ALL));
        }
    }

    private class LinksViewHolder extends ExpandableLinearLayoutViewHolder {

        LinksViewHolder(View view) {
            super(view);
        }

        public void bindModel() {
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean shouldBeVisible = contentView.getVisibility() != View.VISIBLE;
                    contentView.setVisibility(shouldBeVisible ? View.VISIBLE : View.GONE);
                    TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(headerView, R.drawable.ic_website, 0, shouldBeVisible ? R.drawable.ic_expand_less_grey600 : R.drawable.ic_expand_more_grey600, 0);
                }
            });
            headerView.setText(R.string.links);
            TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(headerView, R.drawable.ic_website, 0, R.drawable.ic_expand_more_grey600, 0);
            contentView.removeAllViews();

            // Source button
            if (uriIsSetAndCanBeOpened(app.sourceURL)) {
                addLinkItemView(contentView, R.string.menu_source, R.drawable.ic_source_code, app.sourceURL);
            }

            // Issues button
            if (uriIsSetAndCanBeOpened(app.trackerURL)) {
                addLinkItemView(contentView, R.string.menu_issues, R.drawable.ic_issues, app.trackerURL);
            }

            // Changelog button
            if (uriIsSetAndCanBeOpened(app.changelogURL)) {
                addLinkItemView(contentView, R.string.menu_changelog, R.drawable.ic_changelog, app.changelogURL);
            }

            // Website button
            if (uriIsSetAndCanBeOpened(app.webURL)) {
                addLinkItemView(contentView, R.string.menu_website, R.drawable.ic_website, app.webURL);
            }

            // Email button
            final String subject = Uri.encode(context.getString(R.string.app_details_subject, app.name));
            String emailUrl = app.email == null ? null : ("mailto:" + app.email + "?subject=" + subject);
            if (uriIsSetAndCanBeOpened(emailUrl)) {
                addLinkItemView(contentView, R.string.menu_email, R.drawable.ic_email, emailUrl);
            }
        }
    }

    private class VersionViewHolder extends RecyclerView.ViewHolder {
        final TextView version;
        final TextView status;
        final TextView repository;
        final TextView size;
        final TextView api;
        final TextView incompatibleReasons;
        final TextView buildtype;
        final TextView added;
        final TextView nativecode;

        VersionViewHolder(View view) {
            super(view);
            version = (TextView) view.findViewById(R.id.version);
            status = (TextView) view.findViewById(R.id.status);
            repository = (TextView) view.findViewById(R.id.repository);
            size = (TextView) view.findViewById(R.id.size);
            api = (TextView) view.findViewById(R.id.api);
            incompatibleReasons = (TextView) view.findViewById(R.id.incompatible_reasons);
            buildtype = (TextView) view.findViewById(R.id.buildtype);
            added = (TextView) view.findViewById(R.id.added);
            nativecode = (TextView) view.findViewById(R.id.nativecode);

            int margin = context.getResources().getDimensionPixelSize(R.dimen.layout_horizontal_margin);
            int padding = context.getResources().getDimensionPixelSize(R.dimen.details_activity_padding);
            ViewCompat.setPaddingRelative(view, margin + padding + ViewCompat.getPaddingStart(view), view.getPaddingTop(), ViewCompat.getPaddingEnd(view), view.getPaddingBottom());
        }

        public void bindModel(final Apk apk) {
            java.text.DateFormat df = DateFormat.getDateFormat(context);

            version.setText(context.getString(R.string.version)
                    + " " + apk.versionName
                    + (apk.versionCode == app.suggestedVersionCode ? "  ☆" : ""));

            status.setText(getInstalledStatus(apk));

            repository.setText(context.getString(R.string.repo_provider,
                    RepoProvider.Helper.findById(context, apk.repo).getName()));

            if (apk.size > 0) {
                size.setText(Utils.getFriendlySize(apk.size));
                size.setVisibility(View.VISIBLE);
            } else {
                size.setVisibility(View.GONE);
            }

            if (!Preferences.get().expertMode()) {
                api.setVisibility(View.GONE);
            } else if (apk.minSdkVersion > 0 && apk.maxSdkVersion < Apk.SDK_VERSION_MAX_VALUE) {
                api.setText(context.getString(R.string.minsdk_up_to_maxsdk,
                        Utils.getAndroidVersionName(apk.minSdkVersion),
                        Utils.getAndroidVersionName(apk.maxSdkVersion)));
                api.setVisibility(View.VISIBLE);
            } else if (apk.minSdkVersion > 0) {
                api.setText(context.getString(R.string.minsdk_or_later,
                        Utils.getAndroidVersionName(apk.minSdkVersion)));
                api.setVisibility(View.VISIBLE);
            } else if (apk.maxSdkVersion > 0) {
                api.setText(context.getString(R.string.up_to_maxsdk,
                        Utils.getAndroidVersionName(apk.maxSdkVersion)));
                api.setVisibility(View.VISIBLE);
            }

            if (apk.srcname != null) {
                buildtype.setText("source");
            } else {
                buildtype.setText("bin");
            }

            if (apk.added != null) {
                added.setText(context.getString(R.string.added_on,
                        df.format(apk.added)));
                added.setVisibility(View.VISIBLE);
            } else {
                added.setVisibility(View.GONE);
            }

            if (Preferences.get().expertMode() && apk.nativecode != null) {
                nativecode.setText(TextUtils.join(" ", apk.nativecode));
                nativecode.setVisibility(View.VISIBLE);
            } else {
                nativecode.setVisibility(View.GONE);
            }

            if (apk.incompatibleReasons != null) {
                incompatibleReasons.setText(
                        context.getResources().getString(
                                R.string.requires_features,
                                TextUtils.join(", ", apk.incompatibleReasons)));
                incompatibleReasons.setVisibility(View.VISIBLE);
            } else {
                incompatibleReasons.setVisibility(View.GONE);
            }

            // Disable it all if it isn't compatible...
            final View[] views = {
                    itemView,
                    version,
                    status,
                    repository,
                    size,
                    api,
                    buildtype,
                    added,
                    nativecode,
            };
            for (final View v : views) {
                v.setEnabled(apk.compatible);
            }
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    callbacks.installApk(apk);
                }
            });
        }
    }

    private void addLinkItemView(ViewGroup parent, int resIdText, int resIdDrawable, final String url) {
        TextView view = (TextView) LayoutInflater.from(parent.getContext()).inflate(R.layout.app_details2_link_item, parent, false);
        view.setText(resIdText);
        TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(view, resIdDrawable, 0, 0, 0);
        parent.addView(view);
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onLinkClicked(url);
            }
        });
    }

    private String getInstalledStatus(final Apk apk) {
        // Definitely not installed.
        if (apk.versionCode != app.installedVersionCode) {
            return context.getString(R.string.app_not_installed);
        }
        // Definitely installed this version.
        if (apk.sig != null && apk.sig.equals(app.installedSig)) {
            return context.getString(R.string.app_installed);
        }
        // Installed the same version, but from someplace else.
        final String installerPkgName;
        try {
            installerPkgName = context.getPackageManager().getInstallerPackageName(app.packageName);
        } catch (IllegalArgumentException e) {
            Log.w("AppDetailsAdapter", "Application " + app.packageName + " is not installed anymore");
            return context.getString(R.string.app_not_installed);
        }
        if (TextUtils.isEmpty(installerPkgName)) {
            return context.getString(R.string.app_inst_unknown_source);
        }
        final String installerLabel = InstalledAppProvider
                .getApplicationLabel(context, installerPkgName);
        return context.getString(R.string.app_inst_known_source, installerLabel);
    }

    private void onLinkClicked(String url) {
        if (!TextUtils.isEmpty(url)) {
            callbacks.openUrl(url);
        }
    }

    private final View.OnClickListener onInstallClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            callbacks.installApk();
        }
    };

    private final View.OnClickListener onUnInstallClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            callbacks.uninstallApk();
        }
    };

    private final View.OnClickListener onUpgradeClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            callbacks.upgradeApk();
        }
    };

    private final View.OnClickListener onLaunchClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            callbacks.launchApk();
        }
    };

    private boolean uriIsSetAndCanBeOpened(String s) {
        if (TextUtils.isEmpty(s)) {
            return false;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(s));
        return intent.resolveActivity(context.getPackageManager()) != null;
    }

    /**
     * The HTML formatter adds "\n\n" at the end of every paragraph. This
     * is desired between paragraphs, but not at the end of the whole
     * string as it adds unwanted spacing at the end of the TextView.
     * Remove all trailing newlines.
     * Use this function instead of a trim() as that would require
     * converting to String and thus losing formatting (e.g. bold).
     */
    public static CharSequence trimTrailingNewlines(CharSequence s) {
        if (TextUtils.isEmpty(s)) {
            return s;
        }
        int i;
        for (i = s.length() - 1; i >= 0; i--) {
            if (s.charAt(i) != '\n') {
                break;
            }
        }
        if (i == s.length() - 1) {
            return s;
        }
        return s.subSequence(0, i + 1);
    }

    @SuppressLint("ParcelCreator")
    private static final class SafeURLSpan extends URLSpan {
        SafeURLSpan(String url) {
            super(url);
        }

        @Override
        public void onClick(View widget) {
            try {
                super.onClick(widget);
            } catch (ActivityNotFoundException ex) {
                Toast.makeText(widget.getContext(),
                        widget.getContext().getString(R.string.no_handler_app, getURL()),
                        Toast.LENGTH_LONG).show();
            }
        }
    }
}
