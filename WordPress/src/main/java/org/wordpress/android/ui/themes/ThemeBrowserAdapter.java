package org.wordpress.android.ui.themes;

import android.content.Context;
import android.content.res.Resources;
import android.support.annotation.NonNull;
import android.support.v7.widget.CardView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.wordpress.android.R;
import org.wordpress.android.fluxc.model.ThemeModel;
import org.wordpress.android.ui.prefs.AppPrefs;
import org.wordpress.android.ui.themes.ThemeBrowserFragment.ThemeBrowserFragmentCallback;
import org.wordpress.android.widgets.HeaderGridView;
import org.wordpress.android.widgets.WPNetworkImageView;

import java.util.ArrayList;
import java.util.List;

class ThemeBrowserAdapter extends BaseAdapter implements Filterable {
    private static final String THEME_IMAGE_PARAMETER = "?w=";

    private final Context mContext;
    private final LayoutInflater mInflater;
    private final ThemeBrowserFragmentCallback mCallback;

    private int mViewWidth;
    private String mQuery;

    private final List<ThemeModel> mAllThemes = new ArrayList<>();
    private final List<ThemeModel> mFilteredThemes = new ArrayList<>();

    ThemeBrowserAdapter(Context context, ThemeBrowserFragmentCallback callback) {
        mContext = context;
        mInflater = LayoutInflater.from(context);
        mCallback = callback;
        mViewWidth = AppPrefs.getThemeImageSizeWidth();
    }

    private static class ThemeViewHolder {
        private final CardView mCardView;
        private final WPNetworkImageView mImageView;
        private final TextView mNameView;
        private final TextView mActiveView;
        private final TextView mPriceView;
        private final ImageButton mImageButton;
        private final FrameLayout mFrameLayout;
        private final RelativeLayout mDetailsView;

        ThemeViewHolder(View view) {
            mCardView = view.findViewById(R.id.theme_grid_card);
            mImageView = view.findViewById(R.id.theme_grid_item_image);
            mNameView = view.findViewById(R.id.theme_grid_item_name);
            mPriceView = view.findViewById(R.id.theme_grid_item_price);
            mActiveView = view.findViewById(R.id.theme_grid_item_active);
            mImageButton = view.findViewById(R.id.theme_grid_item_image_button);
            mFrameLayout = view.findViewById(R.id.theme_grid_item_image_layout);
            mDetailsView = view.findViewById(R.id.theme_grid_item_details);
        }
    }

    @Override
    public int getCount() {
        return mFilteredThemes.size();
    }

    @Override
    public Object getItem(int position) {
        return mFilteredThemes.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    void setThemeList(@NonNull List<ThemeModel> themes) {
        mAllThemes.clear();
        mAllThemes.addAll(themes);

        mFilteredThemes.clear();
        mFilteredThemes.addAll(themes);

        if (!TextUtils.isEmpty(mQuery)) {
            getFilter().filter(mQuery);
        } else {
            notifyDataSetChanged();
        }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ThemeViewHolder holder;
        if (convertView == null || convertView.getTag() == null) {
            convertView = mInflater.inflate(R.layout.theme_grid_item, parent, false);
            holder = new ThemeViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (ThemeViewHolder) convertView.getTag();
        }

        configureThemeImageSize(parent);
        ThemeModel theme = mFilteredThemes.get(position);

        String screenshotURL = theme.getScreenshotUrl();
        String themeId = theme.getThemeId();
        boolean isPremium = !theme.isFree();
        boolean isCurrent = theme.getActive();

        holder.mNameView.setText(theme.getName());
        if (isPremium) {
            holder.mPriceView.setText(theme.getPriceText());
            holder.mPriceView.setVisibility(View.VISIBLE);
        } else {
            holder.mPriceView.setVisibility(View.GONE);
        }

        // catch the case where a URL has no protocol
        if (!screenshotURL.startsWith(ThemeWebActivity.THEME_HTTP_PREFIX)) {
            // some APIs return a URL starting with // so the protocol can be supplied by the client
            // strip // before adding the protocol
            if (screenshotURL.startsWith("//")) {
                screenshotURL = screenshotURL.substring(2);
            }
            screenshotURL = ThemeWebActivity.THEME_HTTPS_PROTOCOL + screenshotURL;
        }

        configureImageView(holder, screenshotURL, themeId, isCurrent);
        configureImageButton(holder, themeId, isPremium, isCurrent);
        configureCardView(holder, isCurrent);
        return convertView;
    }

    @SuppressWarnings("deprecation")
    private void configureCardView(ThemeViewHolder themeViewHolder, boolean isCurrent) {
        Resources resources = mContext.getResources();
        if (isCurrent) {
            themeViewHolder.mDetailsView.setBackgroundColor(resources.getColor(R.color.blue_wordpress));
            themeViewHolder.mNameView.setTextColor(resources.getColor(R.color.white));
            themeViewHolder.mActiveView.setVisibility(View.VISIBLE);
            themeViewHolder.mCardView.setCardBackgroundColor(resources.getColor(R.color.blue_wordpress));
        } else {
            themeViewHolder.mDetailsView.setBackgroundColor(resources.getColor(
                    android.support.v7.cardview.R.color.cardview_light_background));
            themeViewHolder.mNameView.setTextColor(resources.getColor(R.color.black));
            themeViewHolder.mActiveView.setVisibility(View.GONE);
            themeViewHolder.mCardView.setCardBackgroundColor(resources.getColor(
                    android.support.v7.cardview.R.color.cardview_light_background));
        }
    }

    private void configureImageView(ThemeViewHolder themeViewHolder, String screenshotURL, final String themeId,
                                    final boolean isCurrent) {
        String requestURL = (String) themeViewHolder.mImageView.getTag();
        if (requestURL == null) {
            requestURL = screenshotURL;
            themeViewHolder.mImageView.setDefaultImageResId(R.drawable.theme_loading);
            themeViewHolder.mImageView.setTag(requestURL);
        }

        if (!requestURL.equals(screenshotURL)) {
            requestURL = screenshotURL;
        }

        themeViewHolder.mImageView
                .setImageUrl(requestURL + THEME_IMAGE_PARAMETER + mViewWidth, WPNetworkImageView.ImageType.PHOTO);
        themeViewHolder.mFrameLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isCurrent) {
                    mCallback.onTryAndCustomizeSelected(themeId);
                } else {
                    mCallback.onViewSelected(themeId);
                }
            }
        });
    }

    private void configureImageButton(ThemeViewHolder themeViewHolder, final String themeId, final boolean isPremium,
                                      boolean isCurrent) {
        final PopupMenu popupMenu = new PopupMenu(mContext, themeViewHolder.mImageButton);
        popupMenu.getMenuInflater().inflate(R.menu.theme_more, popupMenu.getMenu());

        configureMenuForTheme(popupMenu.getMenu(), isCurrent);

        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                int i = item.getItemId();
                if (i == R.id.menu_activate) {
                    if (isPremium) {
                        mCallback.onDetailsSelected(themeId);
                    } else {
                        mCallback.onActivateSelected(themeId);
                    }
                } else if (i == R.id.menu_try_and_customize) {
                    mCallback.onTryAndCustomizeSelected(themeId);
                } else if (i == R.id.menu_view) {
                    mCallback.onViewSelected(themeId);
                } else if (i == R.id.menu_details) {
                    mCallback.onDetailsSelected(themeId);
                } else {
                    mCallback.onSupportSelected(themeId);
                }

                return true;
            }
        });
        themeViewHolder.mImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                popupMenu.show();
            }
        });
    }

    private void configureMenuForTheme(Menu menu, boolean isCurrent) {
        MenuItem activate = menu.findItem(R.id.menu_activate);
        MenuItem customize = menu.findItem(R.id.menu_try_and_customize);
        MenuItem view = menu.findItem(R.id.menu_view);

        if (activate != null) {
            activate.setVisible(!isCurrent);
        }
        if (customize != null) {
            if (isCurrent) {
                customize.setTitle(R.string.customize);
            } else {
                customize.setTitle(R.string.theme_try_and_customize);
            }
        }
        if (view != null) {
            view.setVisible(!isCurrent);
        }
    }

    private void configureThemeImageSize(ViewGroup parent) {
        HeaderGridView gridView = parent.findViewById(R.id.theme_listview);
        int numColumns = gridView.getNumColumns();
        int screenWidth = gridView.getWidth();
        int imageWidth = screenWidth / numColumns;
        if (imageWidth > mViewWidth) {
            mViewWidth = imageWidth;
            AppPrefs.setThemeImageSizeWidth(mViewWidth);
        }
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @SuppressWarnings("unchecked")
            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                mFilteredThemes.clear();
                mFilteredThemes.addAll((List<ThemeModel>) results.values);
                ThemeBrowserAdapter.this.notifyDataSetChanged();
            }

            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                List<ThemeModel> filtered = new ArrayList<>();
                if (TextUtils.isEmpty(constraint)) {
                    mQuery = null;
                    filtered.addAll(mAllThemes);
                } else {
                    mQuery = constraint.toString();
                    String lcConstraint = constraint.toString().toLowerCase();
                    for (ThemeModel theme : mAllThemes) {
                        if (theme.getName().toLowerCase().contains(lcConstraint)) {
                            filtered.add(theme);
                        }
                    }
                }

                FilterResults results = new FilterResults();
                results.values = filtered;

                return results;
            }
        };
    }
}
