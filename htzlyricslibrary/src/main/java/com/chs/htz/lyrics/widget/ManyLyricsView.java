package com.chs.htz.lyrics.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.widget.Scroller;

import com.chs.htz.lyrics.LyricsReader;
import com.chs.htz.lyrics.model.LyricsInfo;
import com.chs.htz.lyrics.model.LyricsLineInfo;
import com.chs.htz.lyrics.utils.ColorUtils;
import com.chs.htz.lyrics.utils.LyricsUtils;
import com.chs.htz.lyrics.utils.TimeUtils;

import java.util.List;
import java.util.TreeMap;

/**
 * @Description: 多行歌词:歌词行号和view所在位置关联,Scroller只做动画处理，不去移动view
 * @param:
 * @return:
 * @throws
 * @author: chaihuasong
 * @date: 2018-04-21 20:28
 */
public class ManyLyricsView extends AbstractLrcView {

    /**
     * 初始
     */
    private final int TOUCHEVENTSTATUS_INIT = 0;

    /**
     * 滑动越界
     */
    private final int TOUCHEVENTSTATUS_OVERSCROLL = 1;
    /**
     * 快速滑动
     */
    private final int TOUCHEVENTSTATUS_FLINGSCROLL = 2;

    /**
     * 触摸状态
     */
    private int mTouchEventStatus = TOUCHEVENTSTATUS_INIT;

    /////////////////////////////////////////////////
    /**
     * 画时间线指示器
     ***/
    private Paint mPaintIndicator;
    /**
     * 画线
     */
    private Paint mPaintLine;

    /**
     * 画线颜色
     */
    private int mPaintLineColor = Color.WHITE;

    /**
     * 绘画播放按钮
     */
    private Paint mPaintPlay;
    /**
     * 播放按钮区域
     */
    private Rect mPlayBtnRect;

    /**
     * 是否在播放按钮区域
     */
    private boolean isInPlayBtnRect = false;
    /**
     * 播放按钮区域字体大小
     */
    private int mPlayRectSize = 32;
    /**
     * 判断view是点击还是移动的距离
     */
    private int mTouchSlop;
    /**
     *
     */
    private Scroller mScroller;
    /**
     * Y轴移动的时间
     */
    private int mDuration = 500;

    ///////////////////////////////////////////////////
    /**
     * 歌词在Y轴上的偏移量
     */
    private float mOffsetY = 0;
    /**
     * 视图y中间
     */
    private float mCentreY = 0;

    private boolean mIsTopMode = true;
    private boolean mTopAnchorInitialized = false;

    /**
     * 颜色渐变梯度
     */
    private int mMaxAlpha = 255;
    private int mMinAlpha = 50;
    // 渐变的高度
    private int mShadeHeight = 0;
    /**
     * 记录手势
     */
    private VelocityTracker mVelocityTracker;
    private int mMaximumVelocity;
    private int mMinimumVelocity;
    // 用于判断拦截
    private int mInterceptX = 0;
    private int mInterceptY = 0;
    /**
     * 触摸最后一次的坐标
     */
    private int mLastY;
    /**
     * 是否直接拦截
     */
    private boolean mIsTouchIntercept = false;

    /**
     * 是否允许触摸
     */
    private boolean mTouchAble = true;

    /**
     * 是否绘画时间线
     */
    private boolean mIsDrawIndicator = true;

    //////////////////////////////////////////////////////

    /**
     * 还原歌词视图
     */
    private final int RESETLRCVIEW = 1;
    /**
     *
     */
    private int mResetDuration = 3000;

    private String currentText;

    /**
     * 行高度累计缓存，避免每帧重复计算
     * mLineHeightCache[i] = 第i行的起始Y坐标
     */
    private int[] mLineHeightCache;
    private boolean mLineHeightCacheValid = false;

    /**
     * Handler处理滑动指示器隐藏和歌词滚动到当前播放的位置
     */
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case RESETLRCVIEW:
                    if (mScroller.computeScrollOffset()) {
                        // 发送还原
                        mHandler.sendEmptyMessageDelayed(RESETLRCVIEW, mResetDuration);
                    } else {
                        mIsTouchIntercept = false;
                        mTouchEventStatus = TOUCHEVENTSTATUS_INIT;
                        int lyricsLineNum = getLyricsLineNum();
                        int deltaY = getLineAtHeightY(lyricsLineNum) - mScroller.getFinalY();
                        mScroller.startScroll(0, mScroller.getFinalY(), 0, deltaY, mDuration);
                        invalidateView();
                    }
                    break;
            }
        }
    };

    /**
     * 歌词快进事件
     */
    private OnLrcClickListener mOnLrcClickListener;
    /**
     * 指示器
     */
    private OnIndicatorListener mOnIndicatorListener;

    public ManyLyricsView(Context context) {
        super(context);
        init(context);
    }

    public ManyLyricsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    /**
     * @throws
     * @Description: 初始
     * @param:
     * @return:
     * @author: chaihuasong
     * @date: 2018-04-21 9:08
     */
    private void init(Context context) {
        // 初始化
        mScroller = new Scroller(context, new LinearInterpolator());
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        final ViewConfiguration configuration = ViewConfiguration.get(getContext());
        mMinimumVelocity = configuration.getScaledMinimumFlingVelocity();
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();

        // 画时间指示器
        mPaintIndicator = new Paint();
        mPaintIndicator.setDither(true);
        mPaintIndicator.setAntiAlias(true);

        // 画线
        mPaintLine = new Paint();
        mPaintLine.setDither(true);
        mPaintLine.setAntiAlias(true);
        mPaintLine.setStyle(Paint.Style.FILL);

        // 绘画播放按钮
        mPaintPlay = new Paint();
        mPaintPlay.setDither(true);
        mPaintPlay.setAntiAlias(true);
        mPaintPlay.setStrokeWidth(2);

        setGotoSearchTextColor(Color.WHITE);
        setGotoSearchTextPressedColor(ColorUtils.parserColor("#0288d1"));

        // 获取屏幕宽度
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        DisplayMetrics displayMetrics = new DisplayMetrics();
        display.getMetrics(displayMetrics);
        int screensWidth = displayMetrics.widthPixels;

        // 设置歌词的最大宽度
        int textMaxWidth = screensWidth / 3 * 2;
        setTextMaxWidth(textMaxWidth);

        // 设置画笔大小
        mPaintIndicator.setTextSize(mPlayRectSize);
        mPaintLine.setTextSize(mPlayRectSize);
        mPaintPlay.setTextSize(mPlayRectSize);
    }

    @Override
    protected void onDrawLrcView(Canvas canvas) {
        if (mShadeHeight == 0) {
            mShadeHeight = getHeight() / 4;
        }
        // 首帧锚定：用 ascent 计算顶部 baseline，避免第一句被“顶出去”
        if (mIsTopMode && !mTopAnchorInitialized && getHeight() > 0) {
            Paint paintHL = getPaintHL();
            Paint.FontMetrics fm = paintHL.getFontMetrics();
            float hlTextHeight = LyricsUtils.getTextHeight(paintHL);
            float topBaseline = getPaddingTop() - fm.ascent;                 // ★ 关键修正
            float centerY = (getHeight() + hlTextHeight) * 0.5f;             // 保持原公式
            float firstLineHeightY = getLineAtHeightY(0);

            float anchorOffset = centerY + firstLineHeightY - topBaseline;   // 让第0行落在顶部 baseline
            mOffsetY = anchorOffset;
            mScroller.setFinalY((int) mOffsetY);
            mTopAnchorInitialized = true;
        }
        drawManyLrcView(canvas);
    }

    @Override
    protected void updateView(long playProgress) {
        updateManyLrcView(playProgress);
    }

    /**
     * 绘画歌词
     *
     * @param canvas
     */
    private void drawManyLrcView(Canvas canvas) {
        // 获取数据
        TreeMap<Integer, LyricsLineInfo> lrcLineInfos = getLrcLineInfos();
        Paint paint = getPaint();              // 普通行画笔
        Paint paintHL = getPaintHL();          // 高亮行画笔
        Paint extraLrcPaint = getExtraLrcPaint();
        int lyricsLineNum = getLyricsLineNum();
        if (lyricsLineNum < 0) lyricsLineNum = 0;

        int splitLyricsLineNum = getSplitLyricsLineNum();
        int splitLyricsWordIndex = getSplitLyricsWordIndex();
        float spaceLineHeight = getSpaceLineHeight();
        float lyricsWordHLTime = getLyricsWordHLTime();

        // 度量
        final Paint.FontMetrics fmNorm = paint.getFontMetrics();
        final float normTextHeight = LyricsUtils.getTextHeight(paint);
        final float hlTextHeight   = LyricsUtils.getTextHeight(paintHL);
        final float lineHeight     = normTextHeight + spaceLineHeight;

        // 顶部/中心参考线
        final float topBaselineRaw = getPaddingTop() - fmNorm.top;      // 使用普通画笔的 top 作为更保守的顶部基线
        final float startBaseline  = Math.max(topBaselineRaw, lineHeight); // ★关键：确保首行Y >= lineHeight，避免被 drawDownLyrics 跳过
        final float centerY        = (getHeight() + hlTextHeight) * 0.5f;

        // 累计高度（基于数据结构）
        final float firstLineY = getLineAtHeightY(0);
        final float curLineY   = getLineAtHeightY(lyricsLineNum);

        // ========== 顶部模式：从第0行开始逐行向下绘制 ==========
        if (mIsTopMode
                && getHeight() > 0
                && lrcLineInfos != null
                && !lrcLineInfos.isEmpty()
                && mTouchEventStatus == TOUCHEVENTSTATUS_INIT
                && !mIsTouchIntercept) {

            float nextY = startBaseline;  // ★ 用修正后的起始 baseline

            for (int i = 0; i < lrcLineInfos.size(); i++) {
                LyricsLineInfo lineInfo = lrcLineInfos.get(i);
                List<LyricsLineInfo> splits = lineInfo.getSplitLyricsLineInfos();
                int   hlSplit = (i == lyricsLineNum) ? splitLyricsLineNum   : -1;
                int   hlWord  = (i == lyricsLineNum) ? splitLyricsWordIndex : -2;
                float hlTime  = (i == lyricsLineNum) ? lyricsWordHLTime     : -1;

                nextY = drawDownLyrics(canvas, paint, paintHL, splits,
                        hlSplit, hlWord, spaceLineHeight, hlTime,
                        nextY);
            }

            // 在“从首行开始”的排布下，当前行的屏幕 Y
            final float curYFromTop = startBaseline + (curLineY - firstLineY);

            // 达到中心线时切回原逻辑（无跳变）
            if (curYFromTop >= centerY - 1f) {
                mIsTopMode = false;
                // 使切换后一帧 mCentreY(center) 与当前视觉位置一致：
                // mCentreY = centerY + curLineY - mOffsetY == curYFromTop
                mOffsetY = centerY + curLineY - curYFromTop;
                mScroller.setFinalY((int) mOffsetY);
            }

            // 顶部模式下通常不显示指示器
            if (mOnIndicatorListener != null) {
                mOnIndicatorListener.indicatorVisibleToUser(false, -1);
            }
            return; // 顶部模式本帧已绘制完成
        }

        // ========== 非顶部模式：保持原“当前行居中 + 上下分块绘制” ==========
        mCentreY = centerY + curLineY - mOffsetY;

        // 画当前行
        LyricsLineInfo lyricsLineInfo = lrcLineInfos.get(lyricsLineNum);
        List<LyricsLineInfo> splitLyricsLineInfos = lyricsLineInfo.getSplitLyricsLineInfos();
        float lineBottomY = drawDownLyrics(
                canvas, paint, paintHL,
                splitLyricsLineInfos,
                splitLyricsLineNum, splitLyricsWordIndex,
                spaceLineHeight, lyricsWordHLTime,
                mCentreY
        );

        // 画当前行之后（向下）
        for (int i = lyricsLineNum + 1; i < lrcLineInfos.size(); i++) {
            LyricsLineInfo down = lrcLineInfos.get(i);
            List<LyricsLineInfo> splits = down.getSplitLyricsLineInfos();
            lineBottomY = drawDownLyrics(
                    canvas, paint, paintHL,
                    splits,
                    -1, -2,
                    spaceLineHeight, -1,
                    lineBottomY
            );
        }

        // 画当前行之前（向上，含额外/翻译/音译）
        float lineTopY = mCentreY;
        for (int i = lyricsLineNum - 1; i >= 0; i--) {
            LyricsLineInfo up = lrcLineInfos.get(i);
            List<LyricsLineInfo> splits = up.getSplitLyricsLineInfos();
            lineTopY = drawUpExtraLyrics(canvas, extraLrcPaint, splits, i, 0, lineTopY);
        }

        // 指示器与时间（原逻辑）
        if (mIsTouchIntercept || mTouchEventStatus != TOUCHEVENTSTATUS_INIT) {
            int scrollLrcLineNum = getScrollLrcLineNum(mOffsetY);
            int startTime = lrcLineInfos.get(scrollLrcLineNum).getStartTime();
            if (mIsDrawIndicator) drawIndicator(canvas, startTime);
            if (mOnIndicatorListener != null) {
                mOnIndicatorListener.indicatorVisibleToUser(true, startTime);
            }
        } else {
            if (mOnIndicatorListener != null) {
                mOnIndicatorListener.indicatorVisibleToUser(false, -1);
            }
        }
    }

    /**
     * 向下绘画动感歌词
     *
     * @param canvas
     * @param paint
     * @param paintHL
     * @param splitLyricsLineInfos 分隔歌词集合
     * @param splitLyricsLineNum   分隔歌词行索引
     * @param splitLyricsWordIndex 分隔歌词字索引
     * @param spaceLineHeight      空行高度
     * @param lyricsWordHLTime     歌词高亮时间
     * @param fristLineTextY       第一行文字位置
     * @return
     */
    private float drawDownLyrics(Canvas canvas, Paint paint, Paint paintHL, List<LyricsLineInfo> splitLyricsLineInfos, int splitLyricsLineNum, int splitLyricsWordIndex, float spaceLineHeight, float lyricsWordHLTime, float fristLineTextY) {
        // 获取数据
        LyricsReader lyricsReader = getLyricsReader();
        int[] paintColors = getPaintColors();
        int[] paintHLColors = getPaintHLColors();

        float lineBottomY = 0;
        int curLyricsLineNum = splitLyricsLineNum;

        // 歌词和空行高度
        float lineHeight = LyricsUtils.getTextHeight(paint) + spaceLineHeight;
        // 往下绘画歌词
        for (int i = 0; i < splitLyricsLineInfos.size(); i++) {
            String text = splitLyricsLineInfos.get(i).getLineLyrics();
            lineBottomY = fristLineTextY + i * lineHeight;

            // 超出上视图
            if (lineBottomY < lineHeight) {
                continue;
            }
            // 超出下视图
            if (lineBottomY + spaceLineHeight > getHeight()) {
                break;
            }

            // 计算颜色透明度
            int alpha = mMaxAlpha;
            if (lineBottomY < mShadeHeight) {
                alpha = mMaxAlpha - (int) ((mShadeHeight - lineBottomY) * (mMaxAlpha - mMinAlpha) / mShadeHeight);
            } else if (lineBottomY > getHeight() - mShadeHeight) {
                alpha = mMaxAlpha - (int) ((lineBottomY - (getHeight() - mShadeHeight)) * (mMaxAlpha - mMinAlpha) / mShadeHeight);
            }
            alpha = Math.max(alpha, 0);
            paint.setAlpha(alpha);
            paintHL.setAlpha(alpha);

            float textWidth = LyricsUtils.getTextWidth(paint, text);
            float textX = (getWidth() - textWidth) * 0.5f;
            if (i < curLyricsLineNum) {
                LyricsUtils.drawText(canvas, paint, paintColors, text, textX, lineBottomY);
                LyricsUtils.drawText(canvas, paintHL, paintHLColors, text, textX, lineBottomY);
                currentText = text;
            } else if (i == curLyricsLineNum) {
                // 绘画动感歌词
                float lineLyricsHLWidth = LyricsUtils.getLineLyricsHLWidth(lyricsReader.getLyricsType(), paint, splitLyricsLineInfos.get(i), splitLyricsWordIndex, lyricsWordHLTime);
                LyricsUtils.drawDynamicText(canvas, paint, paintHL, paintColors, paintHLColors, text, lineLyricsHLWidth, textX, lineBottomY);
                if (i == 1) currentText += text;
                else currentText = text;
            } else if (i > curLyricsLineNum) {
                LyricsUtils.drawText(canvas, paint, paintColors, text, textX, lineBottomY);
            }
        }
        // 考虑部分歌词越界，导致高度不正确，这里重新获取基本歌词结束后的y轴位置
        lineBottomY = fristLineTextY + lineHeight * (splitLyricsLineInfos.size());
        return lineBottomY;
    }

    /**
     * can't get text in background, use {@link #getText} instead
     * @return currentText
     */
    public String getCurrentText() {
        return currentText;
    }

    /**
     * 绘画向下的额外歌词
     *
     * @param canvas
     * @param paint
     * @param paintHL
     * @param lyricsLineNum
     * @param extraSplitLyricsLineNum
     * @param extraSplitLyricsWordIndex
     * @param extraLrcSpaceLineHeight
     * @param lyricsWordHLTime
     * @param translateLyricsWordHLTime
     * @param lineBottomY
     * @return
     */
    private float drawDownExtraLyrics(Canvas canvas, Paint paint, Paint paintHL, int lyricsLineNum, int extraSplitLyricsLineNum, int extraSplitLyricsWordIndex, float extraLrcSpaceLineHeight, float lyricsWordHLTime, float translateLyricsWordHLTime, float lineBottomY) {
        // 获取数据
        LyricsReader lyricsReader = getLyricsReader();
        int extraLrcStatus = getExtraLrcStatus();
        float spaceLineHeight = getSpaceLineHeight();
        int translateDrawType = getTranslateDrawType();
        List<LyricsLineInfo> translateLrcLineInfos = getTranslateLrcLineInfos();
        List<LyricsLineInfo> transliterationLrcLineInfos = getTransliterationLrcLineInfos();

        if (extraLrcStatus == AbstractLrcView.EXTRALRCSTATUS_SHOWTRANSLATELRC) {
            // 画翻译歌词
            if (translateLrcLineInfos != null && translateLrcLineInfos.size() > 0) {
                List<LyricsLineInfo> translateSplitLyricsLineInfos = translateLrcLineInfos.get(lyricsLineNum).getSplitLyricsLineInfos();
                lineBottomY += extraLrcSpaceLineHeight - spaceLineHeight;
                if (lyricsReader.getLyricsType() == LyricsInfo.DYNAMIC && extraLrcStatus == AbstractLrcView.EXTRALRCSTATUS_SHOWTRANSLATELRC && translateDrawType == AbstractLrcView.TRANSLATE_DRAW_TYPE_DYNAMIC) {
                    lineBottomY = drawDownLyrics(canvas, paint, paintHL, translateSplitLyricsLineInfos, extraSplitLyricsLineNum, extraSplitLyricsWordIndex, extraLrcSpaceLineHeight, translateLyricsWordHLTime, lineBottomY);
                } else {
                    int splitLyricsLineNum = -1;
                    if (getTranslateDrawLrcColorType() == AbstractLrcView.TRANSLATE_DRAW_LRC_COLOR_HL) {
                        splitLyricsLineNum = extraSplitLyricsLineNum;
                    }
                    lineBottomY = drawDownLyrics(canvas, paint, paintHL, translateSplitLyricsLineInfos, splitLyricsLineNum, -2, extraLrcSpaceLineHeight, -1, lineBottomY);
                }
                lineBottomY += spaceLineHeight - extraLrcSpaceLineHeight;
            }
        } else if (extraLrcStatus == AbstractLrcView.EXTRALRCSTATUS_SHOWTRANSLITERATIONLRC) {
            // 画音译歌词
            if (transliterationLrcLineInfos != null && transliterationLrcLineInfos.size() > 0) {
                List<LyricsLineInfo> transliterationSplitLrcLineInfos = transliterationLrcLineInfos.get(lyricsLineNum).getSplitLyricsLineInfos();
                lineBottomY += extraLrcSpaceLineHeight - spaceLineHeight;
                lineBottomY = drawDownLyrics(canvas, paint, paintHL, transliterationSplitLrcLineInfos, extraSplitLyricsLineNum, extraSplitLyricsWordIndex, extraLrcSpaceLineHeight, lyricsWordHLTime, lineBottomY);
                lineBottomY += spaceLineHeight - extraLrcSpaceLineHeight;
            }
        }
        return lineBottomY;
    }

    /**
     * 绘画向上的额外歌词
     *
     * @param canvas
     * @param paint
     * @param splitLyricsLineInfos
     * @param lyricsLineNum
     * @param extraLrcSpaceLineHeight
     * @param lineTopY                @return
     */
    private float drawUpExtraLyrics(Canvas canvas, Paint paint, List<LyricsLineInfo> splitLyricsLineInfos, int lyricsLineNum, float extraLrcSpaceLineHeight, float lineTopY) {
        // 获取数据
        int extraLrcStatus = getExtraLrcStatus();
        float spaceLineHeight = getSpaceLineHeight();
        List<LyricsLineInfo> translateLrcLineInfos = getTranslateLrcLineInfos();
        List<LyricsLineInfo> transliterationLrcLineInfos = getTransliterationLrcLineInfos();

        if (extraLrcStatus == AbstractLrcView.EXTRALRCSTATUS_SHOWTRANSLATELRC) {
            // 画翻译歌词
            if (translateLrcLineInfos != null && translateLrcLineInfos.size() > 0) {
                List<LyricsLineInfo> translateSplitLyricsLineInfos = translateLrcLineInfos.get(lyricsLineNum).getSplitLyricsLineInfos();
                lineTopY -= (LyricsUtils.getTextHeight(paint) + spaceLineHeight);
                lineTopY = drawUpLyrics(canvas, paint, translateSplitLyricsLineInfos, extraLrcSpaceLineHeight, lineTopY);
                lineTopY -= (LyricsUtils.getTextHeight(paint) + extraLrcSpaceLineHeight);
                lineTopY = drawUpLyrics(canvas, paint, splitLyricsLineInfos, spaceLineHeight, lineTopY);
            }
        } else if (extraLrcStatus == AbstractLrcView.EXTRALRCSTATUS_SHOWTRANSLITERATIONLRC) {
            // 画音译歌词
            if (transliterationLrcLineInfos != null && transliterationLrcLineInfos.size() > 0) {
                List<LyricsLineInfo> transliterationSplitLrcLineInfos = transliterationLrcLineInfos.get(lyricsLineNum).getSplitLyricsLineInfos();
                lineTopY -= (LyricsUtils.getTextHeight(paint) + spaceLineHeight);
                lineTopY = drawUpLyrics(canvas, paint, transliterationSplitLrcLineInfos, extraLrcSpaceLineHeight, lineTopY);
                lineTopY -= (LyricsUtils.getTextHeight(paint) + extraLrcSpaceLineHeight);
                lineTopY = drawUpLyrics(canvas, paint, splitLyricsLineInfos, spaceLineHeight, lineTopY);
            }
        } else {
            lineTopY -= (LyricsUtils.getTextHeight(paint) + spaceLineHeight);
            lineTopY = drawUpLyrics(canvas, paint, splitLyricsLineInfos, spaceLineHeight, lineTopY);
        }
        return lineTopY;
    }

    /**
     * 向上绘画歌词
     *
     * @param canvas
     * @param paint
     * @param splitLyricsLineInfos 分隔歌词集合
     * @param spaceLineHeight      空行高度
     * @param fristLineTextY       第一行文字位置
     * @return
     */
    private float drawUpLyrics(Canvas canvas, Paint paint, List<LyricsLineInfo> splitLyricsLineInfos, float spaceLineHeight, float fristLineTextY) {
        int[] paintColors = getPaintColors();
        float lineTopY = fristLineTextY;
        // 歌词和空行高度
        float lineHeight = LyricsUtils.getTextHeight(paint) + spaceLineHeight;
        for (int i = splitLyricsLineInfos.size() - 1; i >= 0; i--) {
            if (i != splitLyricsLineInfos.size() - 1) {
                lineTopY -= lineHeight;
            }
            // 超出上视图
            if (lineTopY < lineHeight) {
                break;
            }
            // 超出下视图
            if (lineTopY + spaceLineHeight > getHeight()) {
                continue;
            }
            String text = splitLyricsLineInfos.get(i).getLineLyrics();
            // 计算颜色透明度
            int alpha = mMaxAlpha;
            if (lineTopY < mShadeHeight) {
                alpha = mMaxAlpha - (int) ((mShadeHeight - lineTopY) * (mMaxAlpha - mMinAlpha) / mShadeHeight);
            } else if (lineTopY > getHeight() - mShadeHeight) {
                alpha = mMaxAlpha - (int) ((lineTopY - (getHeight() - mShadeHeight)) * (mMaxAlpha - mMinAlpha) / mShadeHeight);
            }
            alpha = Math.max(alpha, 0);
            paint.setAlpha(alpha);
            float textWidth = LyricsUtils.getTextWidth(paint, text);
            float textX = (getWidth() - textWidth) * 0.5f;
            LyricsUtils.drawText(canvas, paint, paintColors, text, textX, lineTopY);
        }
        // 考虑部分歌词越界，导致高度不正确，这里重新获取基本歌词结束后的y轴位置
        lineTopY = fristLineTextY - lineHeight * (splitLyricsLineInfos.size() - 1);
        return lineTopY;
    }

    /**
     * 绘画时间、播放按钮等
     *
     * @param canvas
     */
    private void drawIndicator(Canvas canvas, int startTime) {
        String timeString = TimeUtils.parseMMSSString(startTime);
        int textHeight = LyricsUtils.getTextHeight(mPaintIndicator);
        float textWidth = LyricsUtils.getTextWidth(mPaintIndicator, timeString);
        int padding = 10;
        float textX = padding;
        float textY = (getHeight() + textHeight) / 2;
        canvas.drawText(timeString, textX, textY, mPaintIndicator);

        mPaintPlay.setStyle(Paint.Style.STROKE);
        // 圆形矩形
        if (mPlayBtnRect == null)
            mPlayBtnRect = new Rect();
        // 圆半径
        int circleR = mPlayRectSize;
        int linePadding = padding * 2;
        int rectR = getWidth() - linePadding;
        int rectL = rectR - circleR * 2;
        int rectT = getHeight() / 2;
        int rectB = rectT + circleR * 2;
        mPlayBtnRect.set(rectL - padding, rectT - padding, rectR + padding, rectB + padding);

        // 画圆
        int cx = rectL + (rectR - rectL) / 2;
        int cy = rectT;
        canvas.drawCircle(cx, cy, circleR, mPaintPlay);

        // 画三角形
        Path trianglePath = new Path();
        float startX = cx + circleR / 2;
        float startY = rectT;
        trianglePath.moveTo(startX, startY);
        float pleftX = startX - (float) circleR / 4 * 3;
        float ptopY = startY - circleR * (float) Math.sqrt(3) / 4;
        float pbomY = startY + circleR * (float) Math.sqrt(3) / 4;
        trianglePath.lineTo(pleftX, ptopY);
        trianglePath.lineTo(pleftX, pbomY);
        trianglePath.close();
        if (isInPlayBtnRect) {
            mPaintPlay.setStyle(Paint.Style.FILL);
        } else {
            mPaintPlay.setStyle(Paint.Style.STROKE);
        }
        canvas.drawPath(trianglePath, mPaintPlay);

        // 画线
        int lineH = 2;
        float lineY = (getHeight() - lineH) / 2;
        float lineLeft = textX + textWidth + linePadding;
        float lineR = rectL - linePadding;
        LinearGradient linearGradientHL = new LinearGradient(lineLeft, lineY + lineH, lineR, lineY + lineH, new int[]{ColorUtils.parserColor(mPaintLineColor, 255), ColorUtils.parserColor(mPaintLineColor, 0), ColorUtils.parserColor(mPaintLineColor, 0), ColorUtils.parserColor(mPaintLineColor, 255)}, new float[]{0f, 0.2f, 0.8f, 1f}, Shader.TileMode.CLAMP);
        mPaintLine.setShader(linearGradientHL);
        canvas.drawRect(lineLeft, lineY, lineR, lineY + lineH, mPaintLine);
    }

    public void updateTopMode(boolean isTopMode) {
        if (mIsTopMode != isTopMode) {
            Log.d("ManyLyricsView", "updateTopMode: " + mIsTopMode + " -> " + isTopMode
                    + ", attached=" + isAttachedToWindow() + ", hasLrc=" + (getLrcLineInfos() != null)
                    + ", lyricsLineNum=" + getLyricsLineNum());
        }
        mIsTopMode = isTopMode;
    }

    /**
     * 重写play方法，确保播放时自动启用滚动动画
     * @param playProgress 播放进度
     */
    @Override
    public void play(int playProgress) {
        Log.d("ManyLyricsView", "play called: progress=" + playProgress
                + ", mIsTopMode=" + mIsTopMode + ", attached=" + isAttachedToWindow()
                + ", visibility=" + getVisibility() + ", width=" + getWidth() + ", height=" + getHeight());
        // ★ 关键：播放时自动禁用顶部模式，确保滚动动画正常工作
        mIsTopMode = false;
        super.play(playProgress);
    }

    /**
     * 更新歌词视图
     *
     * @param playProgress
     */
    private void updateManyLrcView(long playProgress) {
        if (mIsTouchIntercept) return;
        // 获取数据
        LyricsReader lyricsReader = getLyricsReader();
        TreeMap<Integer, LyricsLineInfo> lrcLineInfos = getLrcLineInfos();
        int lyricsLineNum = getLyricsLineNum();

        // ---------- 顶部模式处理已移至 onDrawLrcView，这里只做滚动控制 ----------

        int newLyricsLineNum = LyricsUtils.getLineNumber(lyricsReader.getLyricsType(), lrcLineInfos, playProgress, lyricsReader.getPlayOffset());
        // 防止newLyricsLineNum为-1
        newLyricsLineNum = Math.max(0, newLyricsLineNum);

        // ★ 关键：只要行号变化或者是初始状态（-1），就触发滚动
        if (newLyricsLineNum != lyricsLineNum) {
            if (mTouchEventStatus == TOUCHEVENTSTATUS_INIT && !mIsTopMode) {
                // ★ 非顶部模式：计算滚动动画时长
                final int lineJump = Math.abs(newLyricsLineNum - (lyricsLineNum < 0 ? 0 : lyricsLineNum));
                final int duration;
                if (lineJump <= 3) {
                    // 正常逐行滚动：限制duration在合理范围内（最大1秒）
                    int lineSizeNum = getLineSizeNum(newLyricsLineNum);
                    duration = Math.min((int) (mDuration / mSpeed * lineSizeNum), 1000);
                } else {
                    // 大跳跃（快进/切歌等）：使用短动画，快速到位
                    duration = 200;
                }
                final int targetY = getLineAtHeightY(newLyricsLineNum);
                final int fromLine = lyricsLineNum;
                final int toLine = newLyricsLineNum;

                // ★ 关键修复：Scroller不是线程安全的，必须在主线程中调用startScroll
                Log.d("ManyLyricsView", "[WorkerThread] scheduling startScroll: " + fromLine + "->" + toLine);
                post(() -> {
                    int currentY = mScroller.getFinalY();
                    int deltaY = targetY - currentY;
                    Log.d("ManyLyricsView", "[MainThread] startScroll: lineNum=" + fromLine + "->" + toLine
                            + ", deltaY=" + deltaY + ", duration=" + duration
                            + ", thread=" + Thread.currentThread().getName());
                    mScroller.forceFinished(true);
                    mScroller.startScroll(0, currentY, 0, deltaY, duration);
                    mScrollLogCounter = 0;
                    invalidate();
                });
            }
            lyricsLineNum = newLyricsLineNum;
            setLyricsLineNum(lyricsLineNum);
        }

        updateSplitData(playProgress);
    }

    /**
     * get lyric line with play progress
     * @param playProgress
     * @return lyric text
     */
    public String getText(long playProgress) {
        // 获取数据
        LyricsReader lyricsReader = getLyricsReader();
        TreeMap<Integer, LyricsLineInfo> lrcLineInfos = getLrcLineInfos();
        int newLyricsLineNum = LyricsUtils.getLineNumber(lyricsReader.getLyricsType(), lrcLineInfos, playProgress, lyricsReader.getPlayOffset());
        newLyricsLineNum = Math.max(0, newLyricsLineNum);
        int splitLyricsLineNum = getSplitLyricsLineNum();

        List<LyricsLineInfo> splitLyricsLineInfos = lrcLineInfos.get(newLyricsLineNum).getSplitLyricsLineInfos();
        LyricsLineInfo lyricsLineInfo = splitLyricsLineInfos.get(0);
        String curLyrics = lyricsLineInfo.getLineLyrics();
        if (splitLyricsLineNum > 0 && splitLyricsLineNum < splitLyricsLineInfos.size()) {
            String lrcRightText = splitLyricsLineInfos.get(splitLyricsLineNum).getLineLyrics();
            return curLyrics + lrcRightText;
        }
        return curLyrics;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int lrcStatus = getLrcStatus();
        if (!mTouchAble || lrcStatus != AbstractLrcView.LRCSTATUS_LRC)
            return true;
        obtainVelocityTracker(event);
        int actionId = event.getAction();
        switch (actionId) {
            case MotionEvent.ACTION_DOWN:
                mLastY = (int) event.getY();
                mInterceptX = (int) event.getX();
                mInterceptY = (int) event.getY();
                mHandler.removeMessages(RESETLRCVIEW);
                if (mPlayBtnRect != null && isPlayClick(event)) {
                    isInPlayBtnRect = true;
                    invalidateView();
                }
                break;
            case MotionEvent.ACTION_MOVE:
                int curX = (int) event.getX();
                int curY = (int) event.getY();
                int deltaX = mInterceptX - curX;
                int deltaY = mInterceptY - curY;
                if (mIsTouchIntercept || (Math.abs(deltaY) > mTouchSlop && Math.abs(deltaX) < mTouchSlop)) {
                    mIsTouchIntercept = true;
                    int dy = mLastY - curY;
                    // 创建阻尼效果
                    float finalY = mOffsetY + dy;
                    if (finalY < getTopOverScrollHeightY() || finalY > getBottomOverScrollHeightY()) {
                        dy = dy / 2;
                        mTouchEventStatus = TOUCHEVENTSTATUS_OVERSCROLL;
                    }
                    mScroller.startScroll(0, mScroller.getFinalY(), 0, dy, 0);
                    invalidateView();
                }
                mLastY = curY;
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                if (isInPlayBtnRect) {
                    mHandler.removeMessages(RESETLRCVIEW);
                    if (mOnLrcClickListener != null) {
                        int scrollLrcLineNum = getScrollLrcLineNum(mOffsetY);
                        TreeMap<Integer, LyricsLineInfo> lrcLineInfos = getLrcLineInfos();
                        int startTime = lrcLineInfos.get(scrollLrcLineNum).getStartTime();
                        mOnLrcClickListener.onLrcPlayClicked(startTime + 100);
                    }
                    mIsTouchIntercept = false;
                    mTouchEventStatus = TOUCHEVENTSTATUS_INIT;
                    isInPlayBtnRect = false;
                    invalidateView();
                } else {
                    final VelocityTracker velocityTracker = mVelocityTracker;
                    velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
                    int yVelocity = (int) velocityTracker.getYVelocity();
                    int xVelocity = (int) velocityTracker.getXVelocity();
                    if (Math.abs(yVelocity) > mMinimumVelocity) {
                        int startX = 0;
                        int startY = mScroller.getFinalY();
                        int velocityX = -xVelocity;
                        int velocityY = -yVelocity;
                        int minX = 0;
                        int maxX = 0;
                        TreeMap<Integer, LyricsLineInfo> lrcLineInfos = getLrcLineInfos();
                        int lrcSumHeight = getLineAtHeightY(lrcLineInfos.size());
                        int minY = -getHeight() / 4;
                        int maxY = lrcSumHeight + getHeight() / 4;
                        mScroller.fling(startX, startY, velocityX, velocityY, minX, maxX, minY, maxY);
                        invalidateView();
                        mTouchEventStatus = TOUCHEVENTSTATUS_FLINGSCROLL;
                        mHandler.sendEmptyMessageDelayed(RESETLRCVIEW, mResetDuration);
                    } else {
                        if (mTouchEventStatus == TOUCHEVENTSTATUS_OVERSCROLL) {
                            resetLrcView();
                        } else {
                            mHandler.sendEmptyMessageDelayed(RESETLRCVIEW, mResetDuration);
                        }
                    }
                }
                releaseVelocityTracker();
                mLastY = 0;
                mInterceptX = 0;
                mInterceptY = 0;
                break;
            default:
        }
        return true;
    }

    /**
     * 判断是否是播放按钮点击
     *
     * @param event
     * @return
     */
    private boolean isPlayClick(MotionEvent event) {
        if (mPlayBtnRect == null) return false;
        int x = (int) event.getX();
        int y = (int) event.getY();
        return mPlayBtnRect.contains(x, y);
    }

    /**
     * 判断该行总共有多少行歌词（原始歌词 + 分隔歌词）
     *
     * @param lyricsLineNum
     * @return
     */
    private int getLineSizeNum(int lyricsLineNum) {
        // 防止lyricsLineNum为-1
        if (lyricsLineNum < 0) {
            lyricsLineNum = 0;
        }
        // 获取数据
        TreeMap<Integer, LyricsLineInfo> lrcLineInfos = getLrcLineInfos();
        if (lrcLineInfos == null) {
            return 0;
        }
        int extraLrcStatus = getExtraLrcStatus();
        List<LyricsLineInfo> translateLrcLineInfos = getTranslateLrcLineInfos();
        List<LyricsLineInfo> transliterationLrcLineInfos = getTransliterationLrcLineInfos();

        int lineSizeNum = 0;
        LyricsLineInfo lyricsLineInfo = lrcLineInfos.get(lyricsLineNum);
        if (lyricsLineInfo == null) {
            return 0;
        }
        List<LyricsLineInfo> lyricsLineInfos = lyricsLineInfo.getSplitLyricsLineInfos();
        if (lyricsLineInfos == null) {
            return 0;
        }
        lineSizeNum += lyricsLineInfos.size();

        // 判断是否有翻译歌词或者音译歌词
        if (extraLrcStatus == AbstractLrcView.EXTRALRCSTATUS_SHOWTRANSLATELRC) {
            if (translateLrcLineInfos != null && translateLrcLineInfos.size() > 0) {
                List<LyricsLineInfo> tempTranslateLrcLineInfos = translateLrcLineInfos.get(lyricsLineNum).getSplitLyricsLineInfos();
                lineSizeNum += tempTranslateLrcLineInfos.size();
            }
        } else if (extraLrcStatus == AbstractLrcView.EXTRALRCSTATUS_SHOWTRANSLITERATIONLRC) {
            if (transliterationLrcLineInfos != null && transliterationLrcLineInfos.size() > 0) {
                List<LyricsLineInfo> tempTransliterationLrcLineInfos = transliterationLrcLineInfos.get(lyricsLineNum).getSplitLyricsLineInfos();
                lineSizeNum += tempTransliterationLrcLineInfos.size();
            }
        }
        return lineSizeNum;
    }

    /**
     * 获取所在歌词行的高度（优化版：使用缓存）
     *
     * @param lyricsLineNum
     * @return
     */
    private int getLineAtHeightY(int lyricsLineNum) {
        // 防止lyricsLineNum为-1
        if (lyricsLineNum < 0) {
            return 0;
        }

        TreeMap<Integer, LyricsLineInfo> lrcLineInfos = getLrcLineInfos();
        if (lrcLineInfos == null || lrcLineInfos.isEmpty()) {
            return 0;
        }

        // 检查是否可以使用缓存
        if (mLineHeightCacheValid && mLineHeightCache != null &&
                lyricsLineNum < mLineHeightCache.length) {
            return mLineHeightCache[lyricsLineNum];
        }

        // 重建缓存
        buildLineHeightCache();

        // 使用缓存
        if (mLineHeightCache != null && lyricsLineNum < mLineHeightCache.length) {
            return mLineHeightCache[lyricsLineNum];
        }

        return 0;
    }

    /**
     * 构建行高度缓存
     */
    private void buildLineHeightCache() {
        TreeMap<Integer, LyricsLineInfo> lrcLineInfos = getLrcLineInfos();
        if (lrcLineInfos == null || lrcLineInfos.isEmpty()) {
            mLineHeightCache = null;
            mLineHeightCacheValid = false;
            return;
        }

        Paint paint = getPaint();
        Paint extraLrcPaint = getExtraLrcPaint();
        float spaceLineHeight = getSpaceLineHeight();
        float extraLrcSpaceLineHeight = getExtraLrcSpaceLineHeight();
        int extraLrcStatus = getExtraLrcStatus();
        List<LyricsLineInfo> translateLrcLineInfos = getTranslateLrcLineInfos();
        List<LyricsLineInfo> transliterationLrcLineInfos = getTransliterationLrcLineInfos();

        int size = lrcLineInfos.size();
        mLineHeightCache = new int[size + 1]; // 多一个用于存储总高度
        mLineHeightCache[0] = 0;

        int lineAtHeightY = 0;
        for (int i = 0; i < size; i++) {
            mLineHeightCache[i] = lineAtHeightY;

            LyricsLineInfo lyricsLineInfo = lrcLineInfos.get(i);
            List<LyricsLineInfo> lyricsLineInfos = lyricsLineInfo.getSplitLyricsLineInfos();
            lineAtHeightY += (LyricsUtils.getTextHeight(paint) + spaceLineHeight) * lyricsLineInfos.size();

            if (extraLrcStatus == AbstractLrcView.EXTRALRCSTATUS_SHOWTRANSLATELRC) {
                if (translateLrcLineInfos != null && i < translateLrcLineInfos.size()) {
                    List<LyricsLineInfo> tempTranslateLrcLineInfos = translateLrcLineInfos.get(i).getSplitLyricsLineInfos();
                    lineAtHeightY += (LyricsUtils.getTextHeight(extraLrcPaint) + extraLrcSpaceLineHeight) * tempTranslateLrcLineInfos.size();
                }
            } else if (extraLrcStatus == AbstractLrcView.EXTRALRCSTATUS_SHOWTRANSLITERATIONLRC) {
                if (transliterationLrcLineInfos != null && i < transliterationLrcLineInfos.size()) {
                    List<LyricsLineInfo> tempTransliterationLrcLineInfos = transliterationLrcLineInfos.get(i).getSplitLyricsLineInfos();
                    lineAtHeightY += (LyricsUtils.getTextHeight(extraLrcPaint) + extraLrcSpaceLineHeight) * tempTransliterationLrcLineInfos.size();
                }
            }
        }
        mLineHeightCache[size] = lineAtHeightY;
        mLineHeightCacheValid = true;
    }

    /**
     * 使缓存失效（字体大小、额外歌词状态改变时调用）
     */
    private void invalidateLineHeightCache() {
        mLineHeightCacheValid = false;
    }

    /**
     * 获取滑动的当前行
     *
     * @return
     */
    private int getScrollLrcLineNum(float offsetY) {
        // 获取数据
        TreeMap<Integer, LyricsLineInfo> lrcLineInfos = getLrcLineInfos();
        Paint paint = getPaint();
        Paint extraLrcPaint = getExtraLrcPaint();
        float spaceLineHeight = getSpaceLineHeight();
        float extraLrcSpaceLineHeight = getExtraLrcSpaceLineHeight();
        int extraLrcStatus = getExtraLrcStatus();
        List<LyricsLineInfo> translateLrcLineInfos = getTranslateLrcLineInfos();
        List<LyricsLineInfo> transliterationLrcLineInfos = getTransliterationLrcLineInfos();

        int scrollLrcLineNum = -1;
        int lineHeight = 0;
        for (int i = 0; i < lrcLineInfos.size(); i++) {
            LyricsLineInfo lyricsLineInfo = lrcLineInfos.get(i);
            List<LyricsLineInfo> lyricsLineInfos = lyricsLineInfo.getSplitLyricsLineInfos();
            lineHeight += (LyricsUtils.getTextHeight(paint) + spaceLineHeight) * lyricsLineInfos.size();
            if (extraLrcStatus == AbstractLrcView.EXTRALRCSTATUS_SHOWTRANSLATELRC) {
                if (translateLrcLineInfos != null && translateLrcLineInfos.size() > 0) {
                    List<LyricsLineInfo> tempTranslateLrcLineInfos = translateLrcLineInfos.get(i).getSplitLyricsLineInfos();
                    lineHeight += (LyricsUtils.getTextHeight(extraLrcPaint) + extraLrcSpaceLineHeight) * tempTranslateLrcLineInfos.size();
                }
            } else if (extraLrcStatus == AbstractLrcView.EXTRALRCSTATUS_SHOWTRANSLITERATIONLRC) {
                if (transliterationLrcLineInfos != null && transliterationLrcLineInfos.size() > 0) {
                    List<LyricsLineInfo> tempTransliterationLrcLineInfos = transliterationLrcLineInfos.get(i).getSplitLyricsLineInfos();
                    lineHeight += (LyricsUtils.getTextHeight(extraLrcPaint) + extraLrcSpaceLineHeight) * tempTransliterationLrcLineInfos.size();
                }
            }
            if (lineHeight > offsetY) {
                scrollLrcLineNum = i;
                break;
            }
        }
        if (scrollLrcLineNum == -1) {
            scrollLrcLineNum = lrcLineInfos.size() - 1;
        }
        return scrollLrcLineNum;
    }

    /**
     * @param event
     */
    private void obtainVelocityTracker(MotionEvent event) {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(event);
    }

    /**
     * 释放
     */
    private void releaseVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker.clear();
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    // 用于减少日志输出频率
    private int mScrollLogCounter = 0;
    private static final int SCROLL_LOG_INTERVAL = 100; // 每100帧输出一次日志（约1.6秒）
    private static final float MIN_OFFSET_CHANGE = 5.0f; // 最小偏移变化阈值

    @Override
    public void computeScroll() {
        super.computeScroll();
        // 更新当前的Y轴偏移量
        if (mScroller.computeScrollOffset()) {
            float oldOffsetY = mOffsetY;
            mOffsetY = mScroller.getCurrY();
            // 优化日志：只在有明显变化时输出（变化超过5像素，或每100帧输出一次且有变化超过1像素）
            float offsetChange = Math.abs(mOffsetY - oldOffsetY);
            mScrollLogCounter++;
            if (offsetChange > 50 || (mScrollLogCounter >= SCROLL_LOG_INTERVAL && offsetChange > MIN_OFFSET_CHANGE)) {
                Log.d("ManyLyricsView", "computeScroll: offsetY " + oldOffsetY + " -> " + mOffsetY
                        + ", currY=" + mScroller.getCurrY() + ", finalY=" + mScroller.getFinalY()
                        + ", isFinished=" + mScroller.isFinished());
                mScrollLogCounter = 0;
            }
            invalidateView();
            // Android 12+ 兼容性：使用多种方式确保动画持续
            postInvalidateOnAnimation();
        } else {
            if (mTouchEventStatus == TOUCHEVENTSTATUS_FLINGSCROLL) {
                resetLrcView();
            }
        }
    }

    /**
     * 还原歌词视图
     */
    private void resetLrcView() {
        if (mOffsetY < 0) {
            int deltaY = -mScroller.getFinalY();
            mScroller.startScroll(0, mScroller.getFinalY(), 0, deltaY, mDuration);
            invalidateView();
        } else if (mOffsetY > getBottomOverScrollHeightY()) {
            TreeMap<Integer, LyricsLineInfo> lrcLineInfos = getLrcLineInfos();
            int deltaY = getLineAtHeightY(lrcLineInfos.size() - 1) - mScroller.getFinalY();
            mScroller.startScroll(0, mScroller.getFinalY(), 0, deltaY, mDuration);
            invalidateView();
        }
    }

    /**
     * 获取底部越界
     *
     * @return
     */
    private float getBottomOverScrollHeightY() {
        TreeMap<Integer, LyricsLineInfo> lrcLineInfos = getLrcLineInfos();
        if (lrcLineInfos == null) return 0;
        return getLineAtHeightY(lrcLineInfos.size());
    }

    /**
     * 获取顶部越界高度
     *
     * @return
     */
    private float getTopOverScrollHeightY() {
        return 0;
    }

    /**
     * 指示线颜色
     *
     * @param mPaintLineColor
     */
    public void setPaintLineColor(int mPaintLineColor) {
        this.mPaintLineColor = mPaintLineColor;
    }

    public void setTouchAble(boolean mTouchAble) {
        this.mTouchAble = mTouchAble;
    }

    /**
     * 初始歌词数据
     */
    public void initLrcData() {
        Log.d("ManyLyricsView", "initLrcData: reset mIsTopMode to true, attached=" + isAttachedToWindow());
        mScroller.setFinalY(0);
        mOffsetY = 0;
        mCentreY = 0;
        mTouchEventStatus = TOUCHEVENTSTATUS_INIT;
        setLyricsLineNum(0);
        mIsTopMode = true;  // ★ 开场启用顶部模式
        mTopAnchorInitialized = false;
        invalidateLineHeightCache();  // 清除行高度缓存
        super.initLrcData();
    }

    /**
     * 设置默认颜色
     *
     * @param paintColor
     */
    public void setPaintColor(int[] paintColor) {
        setPaintColor(paintColor, false);
    }

    /**
     * 设置高亮颜色
     *
     * @param paintHLColor
     */
    public void setPaintHLColor(int[] paintHLColor) {
        setPaintHLColor(paintHLColor, false);
    }

    /**
     * 设置高亮颜色
     *
     * @param paintHLColor     至少两种颜色
     * @param isInvalidateView 是否更新视图
     */
    public void setPaintHLColor(int[] paintHLColor, boolean isInvalidateView) {
        mPaintIndicator.setColor(paintHLColor[0]);
        mPaintPlay.setColor(paintHLColor[0]);
        super.setPaintHLColor(paintHLColor, isInvalidateView);
    }

    public void setPlayPaintColor(int paintHLColor) {
        setPlayPaintColor(paintHLColor, false);
    }

    public void setPlayPaintColor(int paintHLColor, boolean isInvalidateView) {
        mPaintPlay.setColor(paintHLColor);
        if (isInvalidateView) {
            invalidateView();
        }
    }

    /**
     * 设置字体文件
     *
     * @param typeFace
     */
    public void setTypeFace(Typeface typeFace) {
        setTypeFace(typeFace, false);
    }

    /**
     * 设置字体文件
     *
     * @param typeFace
     * @param isInvalidateView 是否更新视图
     */
    public void setTypeFace(Typeface typeFace, boolean isInvalidateView) {
        if (isInvalidateView) {
            setTypeFace(typeFace, false);
            resetScrollerFinalY();
        }
        super.setTypeFace(typeFace, isInvalidateView);
    }

    /**
     * //字体大小、额外歌词显示或者空行大小改变，则对歌词的位置进行修改
     * 重置scroller的finaly
     */
    private void resetScrollerFinalY() {
        invalidateLineHeightCache();  // 字体/空行改变，缓存失效
        int lyricsLineNum = getLyricsLineNum();
        if (lyricsLineNum < 0) {
            lyricsLineNum = 0;
        }
        // 字体大小、额外歌词显示或者空行大小改变，则对歌词的位置进行修改
        mOffsetY = getLineAtHeightY(lyricsLineNum);
        mScroller.setFinalY((int) mOffsetY);
    }

    /**
     * 设置空行高度
     *
     * @param spaceLineHeight
     */
    public void setSpaceLineHeight(float spaceLineHeight) {
        setSpaceLineHeight(spaceLineHeight, false);
    }

    /**
     * 设置额外空行高度
     *
     * @param extraLrcSpaceLineHeight
     */
    public void setExtraLrcSpaceLineHeight(float extraLrcSpaceLineHeight) {
        setExtraLrcSpaceLineHeight(extraLrcSpaceLineHeight, false);
    }

    /**
     * 设置额外歌词的显示状态
     *
     * @param extraLrcStatus
     */
    public void setExtraLrcStatus(int extraLrcStatus) {
        super.setExtraLrcStatus(extraLrcStatus);
        resetScrollerFinalY();
        super.setExtraLrcStatus(extraLrcStatus, true);
    }

    /**
     * 设置字体大小
     *
     * @param fontSize
     */
    public void setFontSize(float fontSize) {
        setFontSize(fontSize, false);
    }

    /**
     * 设置字体大小
     *
     * @param fontSize
     * @param isReloadData 是否重新加载数据及刷新界面
     */
    public void setFontSize(float fontSize, boolean isReloadData) {
        if (isReloadData) {
            super.setFontSize(fontSize, false);
            resetScrollerFinalY();
        }
        super.setFontSize(fontSize, isReloadData);
    }

    /**
     * 设置额外字体大小
     *
     * @param extraLrcFontSize
     */
    public void setExtraLrcFontSize(float extraLrcFontSize) {
        setExtraLrcFontSize(extraLrcFontSize, false);
    }

    /**
     * 设置额外字体大小
     *
     * @param extraLrcFontSize
     * @param isReloadData     是否重新加载数据及刷新界面
     */
    public void setExtraLrcFontSize(float extraLrcFontSize, boolean isReloadData) {
        if (isReloadData) {
            super.setExtraLrcFontSize(extraLrcFontSize, false);
            resetScrollerFinalY();
        }
        super.setExtraLrcFontSize(extraLrcFontSize, isReloadData);
    }

    /**
     * 设置歌词解析器
     *
     * @param lyricsReader
     */
    public void setLyricsReader(LyricsReader lyricsReader) {
        invalidateLineHeightCache();  // 清除行高度缓存
        super.setLyricsReader(lyricsReader);
        if (lyricsReader != null && lyricsReader.getLyricsType() == LyricsInfo.DYNAMIC) {
            int extraLrcType = getExtraLrcType();
            // 翻译歌词以动感歌词形式显示
            if (extraLrcType == AbstractLrcView.EXTRALRCTYPE_BOTH || extraLrcType == AbstractLrcView.EXTRALRCTYPE_TRANSLATELRC) {
                super.setTranslateDrawType(AbstractLrcView.TRANSLATE_DRAW_TYPE_DYNAMIC);
            }
        }
        // ★ 关键：重置行号为-1，确保下次updateView时一定会触发滚动动画
        // 因为super.setLyricsReader()内部会调用updateView()并设置lyricsLineNum
        // 如果不重置，后续play()触发的updateView()可能因为行号相同而不触发滚动
        setLyricsLineNum(-1);
        Log.d("ManyLyricsView", "setLyricsReader: reset lyricsLineNum to -1 for animation trigger");
    }

    /**
     * 设置字体大小
     *
     * @param fontSize
     * @param extraFontSize 额外歌词字体
     */
    public void setSize(int fontSize, int extraFontSize) {
        setSize(fontSize, extraFontSize, false);
    }

    /**
     * 设置字体大小
     *
     * @param fontSize
     * @param extraFontSize 额外歌词字体
     * @param isReloadData  是否重新加载数据及刷新界面
     */
    public void setSize(int fontSize, int extraFontSize, boolean isReloadData) {
        if (isReloadData) {
            super.setSize(fontSize, extraFontSize, false);
            resetScrollerFinalY();
        }
        super.setSize(fontSize, extraFontSize, isReloadData);
    }

    /**
     * 是否绘画时间指示器
     *
     * @param isDrawIndicator
     */
    public void setIsDrawIndicator(boolean isDrawIndicator) {
        this.mIsDrawIndicator = isDrawIndicator;
    }

    /**
     * 设置指示器字体大小
     *
     * @param fontSize
     */
    public void setIndicatorFontSize(int fontSize) {
        mPlayBtnRect = null;
        this.mPlayRectSize = fontSize;
        mPaintIndicator.setTextSize(mPlayRectSize);
        mPaintLine.setTextSize(mPlayRectSize);
        mPaintPlay.setTextSize(mPlayRectSize);
        invalidateView();
    }

    public void setDuration(int duration) {
        this.mDuration = duration;
    }

    /**
     * 设置歌词点击事件
     *
     * @param onLrcClickListener
     */
    public void setOnLrcClickListener(OnLrcClickListener onLrcClickListener) {
        this.mOnLrcClickListener = onLrcClickListener;
    }

    /**
     * 设置指示器监听事件
     * @param onIndicatorListener
     */
    public void setOnIndicatorListener(OnIndicatorListener onIndicatorListener) {
        this.mOnIndicatorListener = onIndicatorListener;
    }

    /**
     * 歌词事件
     */
    public interface OnLrcClickListener {
        /**
         * 歌词快进播放
         *
         * @param progress
         */
        void onLrcPlayClicked(int progress);
    }

    /**
     * 指示器事件
     */
    public interface OnIndicatorListener {
        /**
         * 指示器是否可视
         *
         * @param isVisibleToUser   对用户是否可视
         * @param scrollLrcProgress 滑动时的播放进度
         */
        void indicatorVisibleToUser(boolean isVisibleToUser, int scrollLrcProgress);
    }
}