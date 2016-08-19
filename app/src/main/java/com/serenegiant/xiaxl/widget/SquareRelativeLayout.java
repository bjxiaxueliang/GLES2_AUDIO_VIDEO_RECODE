package com.serenegiant.xiaxl.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.RelativeLayout;

/**
 * Created by xiaxl on 2016/08/11.
 * 该Relative的高与宽相同(高有宽度决定)
 */
public class SquareRelativeLayout extends RelativeLayout {

    public SquareRelativeLayout(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, widthMeasureSpec);
    }
}
