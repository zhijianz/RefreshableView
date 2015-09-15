package com.example.zzj.refreshableview;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Created by Administrator on 2015/9/15.
 */
public class RefreshableLayout extends LinearLayout implements View.OnTouchListener {
    private View contentView;
    private View headerView;
    private View footerView;
    private int contentType;    //1:adapterView 2:scrollView 0:else
    private boolean loadOnce = false;
    private boolean refreshable = false;
    private float lastY;
    private int refreshState;
    private final int STATE_HEADER_READY = 0x01;
    private final int STATE_HEADER_REFRESHING = 0x02;
    private final int STATE_HEADER_COMPLETE = 0x03;
    private final int STATE_FOOTER_READER = 0x11;
    private final int STATE_FOOTER_REFRESHING = 0x12;
    private final int STATE_FOOTER_COMPLETE = 0x13;
    private final int STATE_NORMAL = 0x00;
    private MotionEvent downEvent=null;

    public RefreshableLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RefreshableLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        this.setOrientation(VERTICAL);
        headerView = LayoutInflater.from(getContext()).inflate(R.layout.layout_default_ends, null);
        ((TextView) headerView.findViewById(R.id.tv_content)).setText("下拉刷新");
        addView(headerView, 0);
    }

    public int getContentType() {
        contentType = contentView != null ? contentView instanceof AdapterView ? 1 : contentView instanceof ScrollView ? 2 : 0 : 0;
        return contentType;
    }

    public void setOnCreateHeaderListener(OnCreateEndsViewListener listener) {
        createHeaderViewListener = listener;
        headerView = createHeaderViewListener.createEndsView();
    }

    public void setOnCreateFooterListener(OnCreateEndsViewListener listener) {
        createFooterViewListener = listener;
        footerView = createFooterViewListener.createEndsView();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (changed && !loadOnce) {
            contentView = getChildAt(1);
            if (getContentType() == 0)
                throw new RuntimeException("This view must contain a adapterView or scrollView as content");
            contentView.setOnTouchListener(this);
            footerView = LayoutInflater.from(getContext()).inflate(R.layout.layout_default_ends, null);
            ((TextView) footerView.findViewById(R.id.tv_content)).setText("上拉加载更多");
            addView(footerView, 2);
            MarginLayoutParams params = (MarginLayoutParams) headerView.getLayoutParams();
            params.topMargin = -headerView.getMeasuredHeight();
            headerView.setLayoutParams(params);
            params = (MarginLayoutParams) footerView.getLayoutParams();
            params.bottomMargin = -footerView.getMeasuredHeight();
            footerView.setLayoutParams(params);
            loadOnce = true;
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        int diffY=0;
        int action = event.getActionMasked();
        if (action==MotionEvent.ACTION_DOWN&&downEvent==null){
            lastY=event.getY();
            downEvent=event;
            return true;
        }else {
            diffY=(int)(event.getY()-lastY);
            checkRefreshable(diffY);
            lastY=event.getY();
        }
        MarginLayoutParams params;
        if (refreshable){
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (refreshState == STATE_HEADER_READY) {
                        params = (MarginLayoutParams) headerView.getLayoutParams();
                        int margin = params.topMargin + diffY;
                        if (margin <= -headerView.getMeasuredHeight()) {
                            margin = -headerView.getMeasuredHeight();
                            refreshable = false;
                            refreshState = STATE_NORMAL;
                        }
                        params.topMargin = margin;
                        headerView.setLayoutParams(params);
                        createHeaderViewListener.onPullHeight(margin, headerView);
                    } else if (refreshState == STATE_FOOTER_READER) {
                        params = (MarginLayoutParams) footerView.getLayoutParams();
                        int margin = params.bottomMargin + diffY;
                        if (margin <= footerView.getMeasuredHeight()) {
                            margin = -footerView.getMeasuredHeight();
                            refreshable = false;
                            refreshState = STATE_NORMAL;
                        }
                        params.bottomMargin = margin;
                        footerView.setLayoutParams(params);
                        createFooterViewListener.onPullHeight(margin, footerView);
                    }
                    break;
                default:
                    if (refreshState == STATE_HEADER_READY) {
                        params = (MarginLayoutParams) headerView.getLayoutParams();
                        if (params.topMargin >= 0) {
                            refreshState = STATE_HEADER_REFRESHING;
                            createHeaderViewListener.onRefreshingAnimation(headerView);
                        } else {
                            refreshState = STATE_NORMAL;
                            createHeaderViewListener.onDismissAnimation(headerView);
                        }
                    } else if (refreshState == STATE_FOOTER_READER) {
                        params = (MarginLayoutParams) footerView.getLayoutParams();
                        if (params.bottomMargin >= 0) {
                            refreshState = STATE_FOOTER_REFRESHING;
                            createFooterViewListener.onRefreshingAnimation(footerView);
                        } else {
                            refreshState = STATE_NORMAL;
                            createFooterViewListener.onDismissAnimation(footerView);
                        }
                    }
                    refreshable = false;
                    break;
            }
            return true;
        }else {
            if (downEvent!=null){
                contentView.onTouchEvent(downEvent);
                downEvent=null;
            }
            contentView.onTouchEvent(event);
            return false;
        }
    }

    private void checkRefreshable(int diffY) {
        if (!refreshable) {
            if (contentType == 1) {
                getAdapterViewRefreshAble(diffY);
            } else if (contentType == 2) {
                getScrollViewRefreshable(diffY);
            } else {
                refreshable = false;
            }
        }
    }

    private boolean getAdapterViewRefreshAble(int diffY) {
        AdapterView adapterView = (AdapterView) contentView;
        int firstIndex = adapterView.getFirstVisiblePosition();
        View firstChild = adapterView.getChildAt(0);
        int lastIndex = adapterView.getLastVisiblePosition();
        if (firstIndex == 0 && firstChild.getTop() == 0&&diffY>0) {
            this.refreshState = STATE_HEADER_READY;
            refreshable = true;
        } else if (lastIndex == adapterView.getAdapter().getCount()&&diffY<0) {
            this.refreshState = STATE_FOOTER_READER;
            refreshable = true;
        } else {
            this.refreshState = STATE_NORMAL;
            refreshable = false;
        }
        return refreshable;
    }

    private boolean getScrollViewRefreshable(int diffY) {
        if (contentView.getScrollY() == 0&&diffY>0) {
            this.refreshState = STATE_HEADER_READY;
            this.refreshable = true;
        } else if (contentView.getMeasuredHeight() <= contentView.getScrollY() + contentView.getHeight()&&diffY<0) {
            this.refreshState = STATE_FOOTER_READER;
            this.refreshable = true;
        } else {
            this.refreshState = STATE_NORMAL;
            this.refreshable = false;
        }
        return refreshable;
    }

    public interface OnCreateEndsViewListener {
        View createEndsView();

        void onRefreshingAnimation(View endsView);

        void onDismissAnimation(View endsView);

        void onPullHeight(int height, View endsView);
    }

    private OnCreateEndsViewListener createHeaderViewListener = new OnCreateEndsViewListener() {
        @Override
        public View createEndsView() {
            return null;
        }

        @Override
        public void onRefreshingAnimation(final View endsView) {
            ((TextView) endsView.findViewById(R.id.tv_content)).setText("刷新中...");
            endsView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    onDismissAnimation(endsView);
                }
            }, 3000);
        }

        @Override
        public void onDismissAnimation(View endsView) {
            Animation animation = AnimationUtils.loadAnimation(endsView.getContext(), R.anim.anim_dismiss_default_header);
            endsView.startAnimation(animation);
            MarginLayoutParams params=(MarginLayoutParams)endsView.getLayoutParams();
            params.topMargin=-endsView.getMeasuredHeight();
            endsView.setLayoutParams(params);
        }

        @Override
        public void onPullHeight(int height, View endsView) {
            if (Math.abs(height) >= endsView.getMeasuredHeight())
                ((TextView) endsView.findViewById(R.id.tv_content)).setText("放开刷新");
        }
    };
    private OnCreateEndsViewListener createFooterViewListener = new OnCreateEndsViewListener() {
        @Override
        public View createEndsView() {
            return null;
        }

        @Override
        public void onRefreshingAnimation(final View endsView) {
            ((TextView) endsView.findViewById(R.id.tv_content)).setText("加载中...");
            endsView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    onDismissAnimation(endsView);
                }
            }, 3000);
        }

        @Override
        public void onDismissAnimation(View endsView) {
            Animation animation = AnimationUtils.loadAnimation(endsView.getContext(), R.anim.anim_dismiss_default_footer);
            endsView.startAnimation(animation);
            MarginLayoutParams params=(MarginLayoutParams)endsView.getLayoutParams();
            params.topMargin=-endsView.getMeasuredHeight();
            endsView.setLayoutParams(params);
        }

        @Override
        public void onPullHeight(int height, View endsView) {
            if (Math.abs(height) == endsView.getMeasuredState())
                ((TextView) endsView.findViewById(R.id.tv_content)).setText("松开加载更多");
        }
    };
}
